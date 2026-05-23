package com.ordering.system.print

/**
 * ESC/POS 指令常量集。
 *
 * 基于 EPSON ESC/POS 标准协议，所有字节均为十六进制值。
 * 适用于 80mm 热敏票据打印机。
 */
object PosCommands {

    // ========== 初始化与复位 ==========
    /** ESC @ — 初始化打印机，清除缓冲区并恢复默认设置 */
    val INIT = byteArrayOf(0x1B, 0x40)

    // ========== 文本格式 ==========
    /** ESC E 1 — 加粗开 */
    val BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01)
    /** ESC E 0 — 加粗关 */
    val BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00)
    /** ESC - 1 — 下划线开(1点宽) */
    val UNDERLINE_ON = byteArrayOf(0x1B, 0x2D, 0x01)
    /** ESC - 0 — 下划线关 */
    val UNDERLINE_OFF = byteArrayOf(0x1B, 0x2D, 0x00)

    // ========== 字体大小缩放 ==========
    /** ESC ! 0 — 正常大小 */
    val SIZE_NORMAL = byteArrayOf(0x1B, 0x21, 0x00)
    /** ESC ! 16 — 倍高 */
    val SIZE_DOUBLE_HEIGHT = byteArrayOf(0x1B, 0x21, 0x10)
    /** ESC ! 32 — 倍宽 */
    val SIZE_DOUBLE_WIDTH = byteArrayOf(0x1B, 0x21, 0x20)
    /** ESC ! 48 — 倍高 + 倍宽（重点备注用） */
    val SIZE_DOUBLE_BOTH = byteArrayOf(0x1B, 0x21, 0x30)

    // ========== 对齐方式 ==========
    /** ESC a 0 — 左对齐 */
    val ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00)
    /** ESC a 1 — 居中 */
    val ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01)
    /** ESC a 2 — 右对齐 */
    val ALIGN_RIGHT = byteArrayOf(0x1B, 0x61, 0x02)

    // ========== 行间距 ==========
    /** ESC 3 n — 自定义行间距(n 点) */
    fun setLineSpacing(dots: Int): ByteArray =
        byteArrayOf(0x1B, 0x33, dots.toByte())
    /** ESC 2 — 恢复默认行间距 */
    val RESET_LINE_SPACING = byteArrayOf(0x1B, 0x32)

    // ========== 切纸 ==========
    /** GS V 1 — 部分切纸（保留一点连接，便于撕下） */
    val CUT_PARTIAL = byteArrayOf(0x1D, 0x56, 0x01)
    /** GS V 0 — 全切 */
    val CUT_FULL = byteArrayOf(0x1D, 0x56, 0x00)

    // ========== 钱箱 ==========
    /**
     * ESC p m t1 t2 — 打开钱箱。
     * m=0: 钱箱引脚 2
     * t1=0x19(25ms): 脉冲高电平时间
     * t2=0xFF(约500ms): 脉冲低电平时间
     */
    val OPEN_DRAWER = byteArrayOf(0x1B, 0x70, 0x00, 0x19, 0xFF.toByte())

    // ========== 走纸 ==========
    /** 生成 n 行换行字节 */
    fun feedLines(n: Int): ByteArray {
        require(n in 1..255) { "走纸行数必须在 1-255 之间" }
        return ByteArray(n) { 0x0A }
    }
    /** LF — 单次换行 */
    val LINE_FEED = byteArrayOf(0x0A)

    // ========== 字符编码 ==========
    /** ESC t 19 — 选择 GB18030 代码页 */
    val CODE_PAGE_GB18030 = byteArrayOf(0x1B, 0x74, 0x19)
    /** FS & — 进入中文模式 */
    val CHINESE_MODE_ON = byteArrayOf(0x1C, 0x26)
    /** FS . — 退出中文模式 */
    val CHINESE_MODE_OFF = byteArrayOf(0x1C, 0x2E)
}
