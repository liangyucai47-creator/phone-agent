package com.phoneagent

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK
import android.view.accessibility.AccessibilityNodeInfo.ACTION_FOCUS
import android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT

/**
 * 核心无障碍服务：提供屏幕控制能力
 * - 读取 UI 树
 * - 点击/滑动/输入
 * - 系统导航
 */
class PhoneControlService : AccessibilityService() {

    companion object {
        var instance: PhoneControlService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 可选：监听屏幕变化事件
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    // === 核心操作 ===

    /** 读取当前屏幕 UI 树，返回格式化文本 */
    fun readScreenTree(): String {
        val root = rootInActiveWindow ?: return "无法读取屏幕内容"
        val sb = StringBuilder()
        sb.appendLine("=== 屏幕状态 ===")
        sb.appendLine("当前包名: ${root.packageName}")
        val metrics = resources.displayMetrics
        sb.appendLine("屏幕尺寸: ${metrics.widthPixels}x${metrics.heightPixels}")
        sb.appendLine()
        sb.appendLine("=== UI 树 ===")
        dumpNode(root, sb, 0)
        return sb.toString()
    }

    /** 递归遍历 UI 树 */
    private fun dumpNode(node: AccessibilityNodeInfo, sb: StringBuilder, depth: Int) {
        if (depth > 20) return // 防止过深
        val indent = "  ".repeat(depth)
        val className = node.className?.toString() ?: "Unknown"
        val text = node.text?.toString() ?: ""
        val hint = node.hintText?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        val viewId = node.viewIdResourceName ?: ""
        val isClickable = node.isClickable
        val isEditable = node.isEditable
        val bounds = Rect().also { node.getBoundsInScreen(it) }
        val isScrollable = node.isScrollable

        val parts = mutableListOf("[$className]")
        if (text.isNotEmpty()) parts += "text='$text'"
        if (hint.isNotEmpty()) parts += "hint='$hint'"
        if (desc.isNotEmpty()) parts += "desc='$desc'"
        if (viewId.isNotEmpty()) parts += "id='$viewId'"
        if (isClickable) parts += "clickable=true"
        if (isEditable) parts += "editable=true"
        if (isScrollable) parts += "scrollable=true"
        parts += "bounds=$bounds"

        sb.appendLine("$indent${parts.joinToString(" ")}")

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { dumpNode(it, sb, depth + 1) }
        }
    }

    /** 点击坐标 */
    fun click(x: Int, y: Int): Boolean {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription) {}
            override fun onCancelled(gestureDescription: GestureDescription) {}
        }, null)
    }

    /** 滑动 */
    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, duration: Long = 500): Boolean {
        val path = Path().apply {
            moveTo(x1.toFloat(), y1.toFloat())
            lineTo(x2.toFloat(), y2.toFloat())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription) {}
            override fun onCancelled(gestureDescription: GestureDescription) {}
        }, null)
    }

    /** 点击包含指定文本的元素 */
    fun tapByText(text: String): Boolean {
        val node = findNodeByText(rootInActiveWindow, text)
        return node?.performAction(ACTION_CLICK) ?: false
    }

    /** 点击包含指定描述的元素 */
    fun tapByDesc(desc: String): Boolean {
        val node = findNodeByDesc(rootInActiveWindow, desc)
        return node?.performAction(ACTION_CLICK) ?: false
    }

    /** 输入文字 */
    fun inputText(text: String): Boolean {
        val node = findEditableNode(rootInActiveWindow) ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        node.performAction(ACTION_FOCUS)
        return node.performAction(ACTION_SET_TEXT, args)
    }

    /** 系统导航 */
    fun pressBack() = performGlobalAction(GLOBAL_ACTION_BACK)
    fun pressHome() = performGlobalAction(GLOBAL_ACTION_HOME)
    fun pressRecent() = performGlobalAction(GLOBAL_ACTION_RECENTS)

    /** 打开 APP */
    fun openApp(packageName: String): Boolean {
        return try {
            val cmd = "am start -a android.intent.action.MAIN -n $packageName"
            Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            true
        } catch (e: Exception) {
            false
        }
    }

    /** 下拉通知栏 */
    fun openNotifications() = performGlobalAction(16)

    // === 辅助方法 ===

    private fun findNodeByText(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.text?.toString()?.contains(text) == true) return node
        if (node.contentDescription?.toString()?.contains(text) == true) return node
        for (i in 0 until node.childCount) {
            val found = findNodeByText(node.getChild(i), text)
            if (found != null) return found
        }
        return null
    }

    private fun findNodeByDesc(node: AccessibilityNodeInfo?, desc: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.contentDescription?.toString()?.contains(desc) == true) return node
        for (i in 0 until node.childCount) {
            val found = findNodeByDesc(node.getChild(i), desc)
            if (found != null) return found
        }
        return null
    }

    private fun findEditableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable) return node
        if (node.className?.toString() == "android.widget.EditText") return node
        for (i in 0 until node.childCount) {
            val found = findEditableNode(node.getChild(i))
            if (found != null) return found
        }
        return null
    }
}
