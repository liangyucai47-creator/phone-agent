package com.phoneagent

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress

/**
 * WebSocket 服务端：让外部（如 OpenClaw）通过 WebSocket 连接操控手机
 * 默认端口 19876，在前台服务中启动
 */
class PhoneControlWebSocketServer(port: Int = 19876) : WebSocketServer(InetSocketAddress(port)) {

    private val gson = Gson()

    override fun onOpen(conn: WebSocket?, handshake: ClientHandshake?) {
        Log.d(TAG, "客户端已连接: ${conn?.remoteSocketAddress}")
        sendToClient(conn, mapOf("type" to "status", "message" to "已连接 Phone Agent"))
    }

    override fun onClose(conn: WebSocket?, code: Int, reason: String?, by: Boolean) {
        Log.d(TAG, "客户端断开: ${conn?.remoteSocketAddress}")
    }

    override fun onMessage(conn: WebSocket?, message: String?) {
        if (message == null) return
        Log.d(TAG, "收到消息: $message")

        val service = PhoneControlService.instance
        if (service == null) {
            sendToClient(conn, mapOf("type" to "error", "message" to "AccessibilityService 未连接"))
            return
        }

        try {
            val json = JsonParser.parseString(message).asJsonObject
            val action = json.get("action")?.asString ?: ""

            val result = when (action) {
                "read_ui" -> {
                    mapOf("type" to "ui_tree", "data" to service.readScreenTree())
                }
                "click" -> {
                    val x = json.get("x")?.asInt ?: 0
                    val y = json.get("y")?.asInt ?: 0
                    val success = service.click(x, y)
                    mapOf("type" to "result", "action" to "click", "x" to x, "y" to y, "success" to success)
                }
                "swipe" -> {
                    val x1 = json.get("x1")?.asInt ?: 0
                    val y1 = json.get("y1")?.asInt ?: 0
                    val x2 = json.get("x2")?.asInt ?: 0
                    val y2 = json.get("y2")?.asInt ?: 0
                    val duration = json.get("duration")?.asLong ?: 500L
                    val success = service.swipe(x1, y1, x2, y2, duration)
                    mapOf("type" to "result", "action" to "swipe", "success" to success)
                }
                "tap" -> {
                    val text = json.get("text")?.asString ?: ""
                    val success = service.tapByText(text)
                    mapOf("type" to "result", "action" to "tap", "text" to text, "success" to success)
                }
                "tap_desc" -> {
                    val desc = json.get("desc")?.asString ?: ""
                    val success = service.tapByDesc(desc)
                    mapOf("type" to "result", "action" to "tap_desc", "desc" to desc, "success" to success)
                }
                "input" -> {
                    val text = json.get("text")?.asString ?: ""
                    val success = service.inputText(text)
                    mapOf("type" to "result", "action" to "input", "text" to text, "success" to success)
                }
                "back" -> {
                    val success = service.pressBack()
                    mapOf("type" to "result", "action" to "back", "success" to success)
                }
                "home" -> {
                    val success = service.pressHome()
                    mapOf("type" to "result", "action" to "home", "success" to success)
                }
                "recent" -> {
                    val success = service.pressRecent()
                    mapOf("type" to "result", "action" to "recent", "success" to success)
                }
                "screenshot" -> {
                    mapOf("type" to "result", "action" to "screenshot", "success" to false, "message" to "截图功能需要 MediaProjection 授权，暂不可用")
                }
                "open_app" -> {
                    val package_name = json.get("package")?.asString ?: ""
                    val success = service.openApp(package_name)
                    mapOf("type" to "result", "action" to "open_app", "package" to package_name, "success" to success)
                }
                "get_ip" -> {
                    val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
                    var ip = ""
                    while (interfaces.hasMoreElements()) {
                        val iface = interfaces.nextElement()
                        if (iface.name.startsWith("wlan") || iface.name.contains("wifi")) {
                            val addrs = iface.inetAddresses
                            while (addrs.hasMoreElements()) {
                                val addr = addrs.nextElement()
                                if (!addr.isLoopbackAddress && addr.hostAddress?.contains(":") == false) {
                                    ip = addr.hostAddress ?: ""
                                }
                            }
                        }
                    }
                    mapOf("type" to "ip", "ip" to ip, "port" to port.toString())
                }
                else -> {
                    mapOf("type" to "error", "message" to "未知操作: $action")
                }
            }
            sendToClient(conn, result)
        } catch (e: Exception) {
            Log.e(TAG, "处理消息出错", e)
            sendToClient(conn, mapOf("type" to "error", "message" to (e.message ?: "未知错误")))
        }
    }

    override fun onError(conn: WebSocket?, ex: Exception?) {
        Log.e(TAG, "WebSocket 错误", ex)
    }

    override fun onStart() {
        Log.d(TAG, "WebSocket Server 已启动，端口: $port")
    }

    private fun sendToClient(conn: WebSocket?, data: Map<String, Any?>) {
        try {
            conn?.send(gson.toJson(data))
        } catch (e: Exception) {
            Log.e(TAG, "发送消息失败", e)
        }
    }

    /** 广播给所有连接的客户端 */
    fun broadcast(data: Map<String, Any?>) {
        broadcast(gson.toJson(data))
    }

    companion object {
        private const val TAG = "PhoneWS"
        var instance: PhoneControlWebSocketServer? = null
            private set

        fun start(port: Int = 19876) {
            if (instance == null) {
                val server = PhoneControlWebSocketServer(port)
                server.isReuseAddr = true
                server.start()
                instance = server
            }
        }

        fun stop() {
            instance?.stop(0)
            instance = null
        }
    }
}
