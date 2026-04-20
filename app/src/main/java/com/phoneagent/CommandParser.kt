package com.phoneagent

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 指令解析器：将文本指令映射到 AccessibilityService 操作
 *
 * 支持指令：
 *   click(500, 800)          → 点击坐标
 *   swipe(500, 2000, 500, 500) → 滑动（可选第5个参数：时长ms）
 *   tap("发送")               → 点击文本
 *   tapDesc("更多")           → 点击描述
 *   input("你好")             → 输入文字
 *   back                      → 返回
 *   home                      → 首页
 *   recent                    → 最近任务
 *   notifications             → 下拉通知栏
 *   tree                      → 读取UI树
 */
object CommandParser {

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    data class CommandResult(
        val timestamp: String = timeFormat.format(Date()),
        val command: String,
        val result: String,
        val success: Boolean
    )

    /** 解析并执行指令，返回带时间戳的结果 */
    fun execute(input: String, service: PhoneControlService): CommandResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return CommandResult(command = trimmed, result = "空指令，忽略", success = false)
        }

        return try {
            val result = when {
                // click(x, y)
                trimmed.startsWith("click(") && trimmed.endsWith(")") -> {
                    val args = parseArgs(trimmed.removeSurrounding("click(", ")"))
                    require(args.size == 2) { "click 需要 2 个参数：x, y" }
                    val x = args[0].toInt()
                    val y = args[1].toInt()
                    if (service.click(x, y)) "点击 ($x, $y) 成功" else "点击 ($x, $y) 失败"
                }

                // swipe(x1, y1, x2, y2[, duration])
                trimmed.startsWith("swipe(") && trimmed.endsWith(")") -> {
                    val args = parseArgs(trimmed.removeSurrounding("swipe(", ")"))
                    require(args.size in 4..5) { "swipe 需要 4~5 个参数：x1, y1, x2, y2[, duration]" }
                    val x1 = args[0].toInt()
                    val y1 = args[1].toInt()
                    val x2 = args[2].toInt()
                    val y2 = args[3].toInt()
                    val duration = if (args.size == 5) args[4].toLong() else 500L
                    if (service.swipe(x1, y1, x2, y2, duration)) "滑动 ($x1,$y1)→($x2,$y2) ${duration}ms 成功" else "滑动失败"
                }

                // tap("文本")
                trimmed.startsWith("tap(") && trimmed.endsWith(")") -> {
                    val text = trimmed.removeSurrounding("tap(", ")").trim('"', '\'')
                    if (service.tapByText(text)) "点击文本 '$text' 成功" else "未找到文本 '$text'"
                }

                // tapDesc("描述")
                trimmed.startsWith("tapDesc(") && trimmed.endsWith(")") -> {
                    val desc = trimmed.removeSurrounding("tapDesc(", ")").trim('"', '\'')
                    if (service.tapByDesc(desc)) "点击描述 '$desc' 成功" else "未找到描述 '$desc'"
                }

                // input("文字")
                trimmed.startsWith("input(") && trimmed.endsWith(")") -> {
                    val text = trimmed.removeSurrounding("input(", ")").trim('"', '\'')
                    if (service.inputText(text)) "输入 '$text' 成功" else "输入失败（未找到可编辑框）"
                }

                trimmed == "back" -> {
                    if (service.pressBack()) "返回" else "返回失败"
                }

                trimmed == "home" -> {
                    if (service.pressHome()) "回到首页" else "回到首页失败"
                }

                trimmed == "recent" -> {
                    if (service.pressRecent()) "打开最近任务" else "打开最近任务失败"
                }

                trimmed == "notifications" -> {
                    if (service.openNotifications()) "打开通知栏" else "打开通知栏失败"
                }

                trimmed == "tree" -> {
                    service.readScreenTree()
                }

                else -> throw IllegalArgumentException("未知指令: $trimmed")
            }
            CommandResult(command = trimmed, result = result, success = true)
        } catch (e: Exception) {
            CommandResult(command = trimmed, result = "错误: ${e.message}", success = false)
        }
    }

    /** 解析逗号分隔的参数，支持引号内的逗号 */
    private fun parseArgs(raw: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        var quoteChar = ' '

        for (ch in raw) {
            when {
                !inQuotes && (ch == '"' || ch == '\'') -> {
                    inQuotes = true
                    quoteChar = ch
                }
                inQuotes && ch == quoteChar -> {
                    inQuotes = false
                }
                !inQuotes && ch == ',' -> {
                    result.add(current.toString().trim())
                    current = StringBuilder()
                }
                else -> current.append(ch)
            }
        }
        if (current.isNotBlank()) {
            result.add(current.toString().trim())
        }
        return result
    }
}
