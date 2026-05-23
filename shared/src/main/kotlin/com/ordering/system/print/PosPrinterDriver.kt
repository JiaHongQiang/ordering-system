package com.ordering.system.print

import com.ordering.system.print.connection.PrinterInterface
import com.ordering.system.print.template.CustomerReceiptTemplate
import com.ordering.system.print.template.KitchenTicketTemplate

/**
 * ESC/POS 打印驱动核心类。
 *
 * 负责组装指令流、管理连接、输出最终字节数组。
 * 使用 [PrinterInterface] 抽象连接层，支持 USB 和网络打印机。
 *
 * 用法:
 * ```
 * val driver = PosPrinterDriver(printerInterface)
 * driver.open()
 * val bytes = driver.generateKitchenTicket(order)
 * driver.print(bytes)
 * driver.close()
 * ```
 *
 * @param connection 打印机连接实现
 * @param charset 字符编码，默认 GB18030
 */
class PosPrinterDriver(
    private val connection: PrinterInterface,
    private val charset: String = "GB18030"
) {

    /**
     * 打开连接并初始化打印机。
     * 发送 INIT → 选择 GB18030 代码页 → 进入中文模式。
     */
    fun open() {
        connection.open()
        connection.write(PosCommands.INIT)
        connection.write(PosCommands.CODE_PAGE_GB18030)
        connection.write(PosCommands.CHINESE_MODE_ON)
        connection.flush()
    }

    /**
     * 发送字节数据到打印机。
     * 调用后自动 flush 确保数据立即发送。
     */
    fun print(data: ByteArray) {
        check(connection.isConnected) { "打印机未连接" }
        connection.write(data)
        connection.flush()
    }

    /** 关闭连接 */
    fun close() {
        connection.close()
    }

    /** 生成厨房票字节数组 */
    fun generateKitchenTicket(order: OrderPrintData): ByteArray {
        return KitchenTicketTemplate(charset).build(order)
    }

    /** 生成客户结账票字节数组 */
    fun generateCustomerReceipt(order: OrderPrintData): ByteArray {
        return CustomerReceiptTemplate(charset).build(order)
    }

    /** 生成开钱箱指令 */
    fun generateOpenDrawerCommand(): ByteArray = PosCommands.OPEN_DRAWER
}

// ========== 打印数据模型 ==========

/**
 * 订单打印数据，与数据库解耦的纯数据类。
 * 由上层业务从 [com.ordering.system.data.db.entity.Order] 转化而来。
 */
data class OrderPrintData(
    val orderId: Long,
    val orderNumber: String,
    val tableNumber: String,
    val createdAt: String,
    val items: List<OrderItemPrintData>,
    val totalAmount: Double,
    val storeName: String = "",
    val storePhone: String = "",
    val storeTagline: String = "",
    val receiptFooter: String = "感谢光临，欢迎再来!",
    val notes: String = "",
    val createdBy: String = ""
)

data class OrderItemPrintData(
    val productName: String,
    val quantity: Int,
    val unitPrice: Double,
    val lineTotal: Double,
    val selectedSize: String = "",
    val modifiers: List<ModifierPrintData> = emptyList(),
    val notes: String = ""
)

data class ModifierPrintData(
    val groupName: String,
    val modifierName: String,
    val price: Double,
    val quantity: Int = 1
)
