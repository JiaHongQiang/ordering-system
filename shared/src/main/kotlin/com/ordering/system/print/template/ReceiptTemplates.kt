package com.ordering.system.print.template

import com.ordering.system.print.OrderItemPrintData
import com.ordering.system.print.OrderPrintData
import com.ordering.system.print.PosCommands
import java.io.ByteArrayOutputStream

/**
 * 收据/票据模板基类。
 *
 * 提供 GB18030 编码输出、文本宽度计算、对齐排版等基础能力。
 * 子类实现 [build] 方法定义具体票据格式。
 *
 * @param charset 字符编码，默认 GB18030
 */
abstract class ReceiptTemplate(protected val charset: String = "GB18030") {

    companion object {
        /** 80mm 热敏纸标准字符宽度（中文字符占 2 列） */
        const val LINE_WIDTH = 48
    }

    protected val buffer = ByteArrayOutputStream(4096)

    /** 构建完整票据字节数组 */
    abstract fun build(order: OrderPrintData): ByteArray

    // ========== 底层输出 ==========

    protected fun writeCommand(vararg commands: ByteArray) {
        commands.forEach { buffer.write(it) }
    }

    protected fun writeText(text: String) {
        buffer.write(text.toByteArray(charset(charset)))
    }

    protected fun writeLine(text: String) {
        writeText(text)
        writeCommand(PosCommands.LINE_FEED)
    }

    protected fun writeEmptyLine(count: Int = 1) {
        writeCommand(PosCommands.feedLines(count))
    }

    protected fun writeDivider(char: Char = '=', width: Int = LINE_WIDTH) {
        writeLine(String(CharArray(width) { char }))
    }

    // ========== 文本宽度计算 (GB18030) ==========

    /**
     * 计算字符串在收据上的显示列宽。
     * 中文/全角字符占 2 列，ASCII 占 1 列。
     */
    protected fun displayWidth(text: String): Int {
        var width = 0
        for (ch in text) {
            width += if (isFullWidth(ch)) 2 else 1
        }
        return width
    }

    /** 截断文本到指定列宽 */
    protected fun truncateToWidth(text: String, maxWidth: Int): String {
        val sb = StringBuilder()
        var width = 0
        for (ch in text) {
            val cw = if (isFullWidth(ch)) 2 else 1
            if (width + cw > maxWidth) break
            sb.append(ch)
            width += cw
        }
        return sb.toString()
    }

    /** 左对齐：文本 + 空格填充 */
    protected fun padRight(text: String, targetWidth: Int): String {
        val padding = maxOf(0, targetWidth - displayWidth(text))
        return text + " ".repeat(padding)
    }

    /** 右对齐：空格填充 + 文本 */
    protected fun padLeft(text: String, targetWidth: Int): String {
        val padding = maxOf(0, targetWidth - displayWidth(text))
        return " ".repeat(padding) + text
    }

    /**
     * 生成 "左文本....右文本" 格式的行（点号填充）。
     */
    protected fun buildDotLine(left: String, right: String, totalWidth: Int = LINE_WIDTH): String {
        val leftW = displayWidth(left)
        val rightW = displayWidth(right)
        val dots = totalWidth - leftW - rightW
        return if (dots >= 2) {
            left + ".".repeat(dots) + right
        } else {
            truncateToWidth(left, totalWidth - rightW - 1) + " " + right
        }
    }

    /**
     * 生成 "左文本    右文本" 格式的行（空格分隔）。
     */
    protected fun buildAlignedLine(left: String, right: String, totalWidth: Int = LINE_WIDTH): String {
        val leftW = displayWidth(left)
        val rightW = displayWidth(right)
        val spaces = totalWidth - leftW - rightW
        return if (spaces >= 1) {
            left + " ".repeat(spaces) + right
        } else {
            truncateToWidth(left, totalWidth - rightW - 1) + " " + right
        }
    }

    protected fun formatPrice(price: Double): String = String.format("%.2f", price)

    private fun isFullWidth(ch: Char): Boolean {
        val c = ch.code
        return c in 0x1100..0x115F || c in 0x2E80..0x2EFF || c in 0x2F00..0x2FDF ||
                c in 0x3000..0x303F || c in 0x3040..0x309F || c in 0x30A0..0x30FF ||
                c in 0x3100..0x312F || c in 0x3130..0x318F || c in 0x3190..0x319F ||
                c in 0x31A0..0x31BF || c in 0x31C0..0x31EF || c in 0x31F0..0x31FF ||
                c in 0x3200..0x32FF || c in 0x3300..0x33FF || c in 0x3400..0x4DBF ||
                c in 0x4E00..0x9FFF || c in 0xA000..0xA4CF || c in 0xAC00..0xD7AF ||
                c in 0xF900..0xFAFF || c in 0xFE30..0xFE4F || c in 0xFF00..0xFF60 ||
                c in 0xFFE0..0xFFE6
    }
}

// ========== 厨房票模板 ==========

/**
 * 厨房票模板。
 *
 * 设计要点：
 * - 隐藏金额，避免后厨看到价格
 * - 单号和台号使用大号字体居中，确保醒目
 * - 加料备注（如"不加葱"、"多加面"）使用加粗 + 倍高倍宽字体
 */
class KitchenTicketTemplate(charset: String = "GB18030") : ReceiptTemplate(charset) {

    override fun build(order: OrderPrintData): ByteArray {
        buffer.reset()
        writeCommand(PosCommands.INIT, PosCommands.CODE_PAGE_GB18030, PosCommands.CHINESE_MODE_ON)

        // === 台号：大号居中 ===
        writeCommand(PosCommands.ALIGN_CENTER, PosCommands.SIZE_DOUBLE_BOTH, PosCommands.BOLD_ON)
        writeLine("台 ${order.tableNumber}")
        writeCommand(PosCommands.SIZE_NORMAL, PosCommands.BOLD_OFF)

        // === 单号：加粗 ===
        writeCommand(PosCommands.BOLD_ON)
        writeLine("单号: ${order.orderNumber}")
        writeCommand(PosCommands.BOLD_OFF)

        writeLine("时间: ${order.createdAt}")
        writeDivider('-')

        // === 明细 ===
        order.items.forEach { item ->
            // 菜名倍高加粗 + 右侧数量
            val sizeLabel = if (item.selectedSize.isNotBlank()) "(${item.selectedSize})" else ""
            val displayName = item.productName + sizeLabel
            val qtyStr = "x${item.quantity}"

            writeCommand(PosCommands.BOLD_ON, PosCommands.SIZE_DOUBLE_HEIGHT)
            writeLine(buildAlignedLine(displayName, qtyStr))
            writeCommand(PosCommands.SIZE_NORMAL, PosCommands.BOLD_OFF)

            // 加料备注：加粗 + 倍高倍宽（重点突出）
            item.modifiers.forEach { mod ->
                writeCommand(PosCommands.BOLD_ON, PosCommands.SIZE_DOUBLE_BOTH)
                writeLine("  >> ${mod.modifierName}")
                writeCommand(PosCommands.SIZE_NORMAL, PosCommands.BOLD_OFF)
            }

            // 单品备注（如"不加葱"、"多加面"）
            if (item.notes.isNotBlank()) {
                writeCommand(PosCommands.BOLD_ON, PosCommands.SIZE_DOUBLE_BOTH)
                writeLine("  !! ${item.notes}")
                writeCommand(PosCommands.SIZE_NORMAL, PosCommands.BOLD_OFF)
            }

            writeEmptyLine()
        }

        // === 整单备注 ===
        if (order.notes.isNotBlank()) {
            writeDivider('-')
            writeCommand(PosCommands.BOLD_ON, PosCommands.SIZE_DOUBLE_BOTH)
            writeLine("备注: ${order.notes}")
            writeCommand(PosCommands.SIZE_NORMAL, PosCommands.BOLD_OFF)
        }

        // === 切纸 ===
        writeEmptyLine(3)
        writeCommand(PosCommands.CUT_PARTIAL)
        return buffer.toByteArray()
    }
}

// ========== 客户结账票模板 ==========

/**
 * 客户结账票模板。
 *
 * 设计要点：
 * - 店铺名称居中大号显示
 * - 包含订单号、交易时间
 * - 列对齐输出商品名、数量、小计
 * - 页脚：总计金额、税款明细、欢迎词
 */
class CustomerReceiptTemplate(charset: String = "GB18030") : ReceiptTemplate(charset) {

    override fun build(order: OrderPrintData): ByteArray {
        buffer.reset()
        writeCommand(PosCommands.INIT, PosCommands.CODE_PAGE_GB18030, PosCommands.CHINESE_MODE_ON)

        // === 店铺名称：居中大号 ===
        writeCommand(PosCommands.ALIGN_CENTER, PosCommands.SIZE_DOUBLE_BOTH, PosCommands.BOLD_ON)
        writeLine(order.storeName)
        writeCommand(PosCommands.SIZE_NORMAL, PosCommands.BOLD_OFF)

        writeCommand(PosCommands.ALIGN_CENTER)
        if (order.storeTagline.isNotBlank()) writeLine(order.storeTagline)
        writeCommand(PosCommands.ALIGN_LEFT)

        // === 订单信息 ===
        writeLine("单号: ${order.orderNumber}")
        writeLine("时间: ${order.createdAt}")

        writeDivider('=')

        // === 表头 ===
        // 商品名称(左) 数量(中) 金额(右)
        val col1 = 20  // 名称列宽
        val col2 = 6   // 数量列宽
        val col3 = 8   // 金额列宽
        writeLine(
            padRight("商品名称", col1) +
            padRight("数量", col2) +
            padLeft("金额", col3)
        )
        writeDivider('-')

        // === 明细 ===
        order.items.forEach { item ->
            // 商品名 + 数量 + 金额 一行
            val name = truncateToWidth(item.productName, col1)
            val qty = "×${item.quantity}"
            val total = formatPrice(item.lineTotal)
            writeCommand(PosCommands.BOLD_ON)
            writeLine(
                padRight(name, col1) +
                padRight(qty, col2) +
                padLeft(total, col3)
            )
            writeCommand(PosCommands.BOLD_OFF)

            // 核心属性（类型/规格）加粗显示
            val coreAttrs = mutableListOf<String>()
            if (item.selectedSize.isNotBlank()) coreAttrs.add(item.selectedSize)
            // 把没有价格的小料当作核心属性（如 汤面、辣度）
            val freeMods = item.modifiers.filter { it.price <= 0.0 }
            val paidMods = item.modifiers.filter { it.price > 0.0 }
            freeMods.forEach { coreAttrs.add(it.modifierName) }

            if (coreAttrs.isNotEmpty()) {
                writeCommand(PosCommands.BOLD_ON)
                writeLine("  【${coreAttrs.joinToString(" / ")}】")
                writeCommand(PosCommands.BOLD_OFF)
            }

            // 付费小料：[组名] 名称 (¥价格) ×数量
            paidMods.forEach { mod ->
                val modQty = if (mod.quantity > 1) " ×${mod.quantity}" else ""
                val modPrice = formatPrice(mod.price * mod.quantity)
                writeLine("  [${mod.groupName}] ${mod.modifierName} (¥$modPrice)$modQty")
            }

            // 备注
            if (item.notes.isNotBlank()) {
                writeLine("  ※ 备注：${item.notes}")
            }
        }

        writeDivider('=')

        // === 汇总 ===
        writeCommand(PosCommands.BOLD_ON, PosCommands.SIZE_DOUBLE_HEIGHT)
        writeLine(buildAlignedLine("合计:", formatPrice(order.totalAmount)))
        writeCommand(PosCommands.SIZE_NORMAL, PosCommands.BOLD_OFF)

        // 订单备注
        if (order.notes.isNotBlank()) {
            writeLine("※ 备注：${order.notes}")
        }

        // === 页脚 ===
        writeDivider('=')
        writeCommand(PosCommands.ALIGN_CENTER)
        writeLine(order.receiptFooter)
        writeCommand(PosCommands.ALIGN_LEFT)

        writeEmptyLine(3)
        writeCommand(PosCommands.CUT_PARTIAL)
        return buffer.toByteArray()
    }
}
