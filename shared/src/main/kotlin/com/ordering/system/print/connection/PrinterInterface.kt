package com.ordering.system.print.connection

import java.io.BufferedOutputStream
import java.io.Closeable
import java.net.InetSocketAddress
import java.net.Socket

/**
 * 打印机连接接口。
 *
 * 定义打印机通信的标准协议，支持 open → write → close 生命周期。
 * 实现类需保证线程安全和资源释放。
 */
interface PrinterInterface : Closeable {

    /** 打开连接 */
    fun open()

    /**
     * 发送原始字节到打印机。
     * 实现类应进行缓冲处理以减少 I/O 频次。
     */
    fun write(data: ByteArray)

    /**
     * 将缓冲区数据强制刷出到打印机。
     * 在发送切纸/钱箱等关键指令前应调用。
     */
    fun flush()

    /** 当前连接是否可用 */
    val isConnected: Boolean

    /** 打印机标识名称，用于日志 */
    val printerName: String

    override fun close()
}

/**
 * 网络打印机连接器（TCP Socket）。
 *
 * 使用 [BufferedOutputStream] 进行字节流优化，减少网络 I/O 频次。
 * 支持连接超时和写入超时配置，处理网络断开等异常情况。
 *
 * 用法:
 * ```
 * val connector = NetworkPrinterConnector("192.168.1.100", 9100)
 * connector.open()
 * connector.write(data)
 * connector.flush()
 * connector.close()
 * ```
 *
 * @param host 打印机 IP 地址
 * @param port 端口号，默认 9100（ESC/POS 标准端口）
 * @param connectTimeoutMs 连接超时（毫秒）
 * @param writeTimeoutMs 写入/读取超时（毫秒）
 * @param bufferSize 缓冲区大小（字节），默认 8KB
 */
class NetworkPrinterConnector(
    private val host: String,
    private val port: Int = 9100,
    private val connectTimeoutMs: Int = 5000,
    private val writeTimeoutMs: Int = 3000,
    private val bufferSize: Int = 8192
) : PrinterInterface {

    private var socket: Socket? = null
    private var outputStream: BufferedOutputStream? = null

    override val printerName: String
        get() = "NET[$host:$port]"

    override val isConnected: Boolean
        get() = socket?.isConnected == true && socket?.isClosed == false

    /**
     * 打开 TCP 连接。
     *
     * @throws PrinterConnectionException 连接超时或拒绝时抛出
     */
    override fun open() {
        try {
            socket = Socket().apply {
                connect(InetSocketAddress(host, port), connectTimeoutMs)
                soTimeout = writeTimeoutMs
                tcpNoDelay = true
                setKeepAlive(true)
            }
            outputStream = BufferedOutputStream(socket!!.getOutputStream(), bufferSize)
        } catch (e: Exception) {
            close()
            throw PrinterConnectionException("无法连接到打印机 $host:$port: ${e.message}", e)
        }
    }

    /**
     * 写入数据到缓冲区。
     * 数据不会立即发送到打印机，需调用 [flush] 强制刷出。
     *
     * @throws PrinterConnectionException 连接已断开时抛出
     */
    override fun write(data: ByteArray) {
        check(isConnected) { "打印机连接未打开: $printerName" }
        try {
            outputStream?.apply {
                write(data)
            }
        } catch (e: Exception) {
            close()
            throw PrinterConnectionException("写入数据失败: ${e.message}", e)
        }
    }

    /**
     * 刷出缓冲区数据到打印机。
     * 在发送关键指令（切纸、钱箱）前必须调用。
     */
    override fun flush() {
        try {
            outputStream?.flush()
        } catch (e: Exception) {
            close()
            throw PrinterConnectionException("刷新缓冲区失败: ${e.message}", e)
        }
    }

    /**
     * 关闭连接并释放资源。
     */
    override fun close() {
        try { outputStream?.flush() } catch (_: Exception) {}
        try { outputStream?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        outputStream = null
        socket = null
    }
}

/**
 * USB 打印机连接器（Android 环境）。
 *
 * 基础框架，需在 Android 模块中注入 UsbDevice 依赖。
 *
 * @param vendorId USB 设备厂商 ID
 * @param productId USB 设备产品 ID
 */
class UsbPrinterConnector(
    private val vendorId: Int,
    private val productId: Int
) : PrinterInterface {

    // Android 环境下注入:
    // private var usbDevice: UsbDevice? = null
    // private var usbConnection: UsbDeviceConnection? = null
    // private var usbEndpoint: UsbEndpoint? = null

    private var _isConnected = false

    override val printerName: String
        get() = "USB[$vendorId:${productId}]"

    override val isConnected: Boolean
        get() = _isConnected

    override fun open() {
        // TODO: Android 实现
        // 1. 通过 UsbManager 获取 UsbDevice
        // 2. 请求权限 → openDevice
        // 3. 找到输出端点 (USB_DIR_OUT | USB_ENDPOINT_XFER_BULK)
        // 4. claimInterface
        _isConnected = true
    }

    override fun write(data: ByteArray) {
        check(_isConnected) { "USB 连接未打开" }
        // TODO: Android 实现 — usbConnection.bulkTransfer(endpoint, data, data.size, TIMEOUT)
    }

    override fun flush() {
        // USB bulk transfer 无需显式 flush
    }

    override fun close() {
        // TODO: releaseInterface, close
        _isConnected = false
    }
}

/**
 * 打印机连接异常。
 */
class PrinterConnectionException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
