package com.rmms.printspooler

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.UUID

class ThermalPrintEngine(private val context: Context) {
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    companion object {
        fun calculateAutoMaxChars(
            widthMm: Int,
            marginMm: Int,
            printMode: String,
            isBold: Boolean = false,
            customOverride: Int = 0
        ): Int {
            if (customOverride > 0) {
                return if (isBold) (customOverride * 88) / 100 else customOverride
            }

            val totalDots = widthMm * 8
            val totalMarginDots = (marginMm.coerceAtLeast(1) * 2) * 8
            val usableWidthDots = (totalDots - totalMarginDots).coerceAtLeast(120)

            return if (printMode == "TSPL_LABEL") {
                val charDots = if (isBold) 18 else 16
                (usableWidthDots / charDots).coerceAtLeast(8)
            } else {
                val charDots = if (isBold) 14 else 13
                (usableWidthDots / charDots).coerceAtLeast(10)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun getPairedPrinters(): List<BluetoothDevice> {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter: BluetoothAdapter? = bluetoothManager.adapter

        return adapter?.bondedDevices?.toList() ?: emptyList()
    }

    @SuppressLint("MissingPermission")
    suspend fun printDynamicData(
        profile: PrinterProfile,
        template: LabelTemplate,
        payloadMap: Map<String, Any>
    ): Result<String> = withContext(Dispatchers.IO) {
        if (profile.printerMac.isEmpty()) {
            return@withContext Result.failure(Exception("Printer belum dipilih pada profil '${profile.profileName}'."))
        }

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
            ?: return@withContext Result.failure(Exception("Bluetooth tidak tersedia."))

        if (!adapter.isEnabled) {
            return@withContext Result.failure(Exception("Bluetooth perangkat sedang mati."))
        }

        val resolvedLines = resolveTemplateLines(template.bodyPattern, payloadMap)
        val commandBytes = when (profile.printMode) {
            "TSPL_LABEL" -> buildTsplFromTemplate(profile, resolvedLines)
            else -> buildEscPosFromTemplate(profile, resolvedLines)
        }

        val device = adapter.getRemoteDevice(profile.printerMac)
        var lastError: Exception? = null

        for (attempt in 1..2) {
            val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            try {
                adapter.cancelDiscovery()
                socket.connect()

                val outputStream: OutputStream = socket.outputStream
                outputStream.write(commandBytes)
                outputStream.flush()
                delay(600)
                socket.close()

                return@withContext Result.success("Cetak berhasil (${profile.profileName})")
            } catch (e: Exception) {
                lastError = e
                try { socket.close() } catch (_: Exception) {}
                if (attempt == 1) {
                    delay(600)
                }
            }
        }

        Result.failure(Exception("Gagal terhubung ke ${profile.printerName}: ${lastError?.message}"))
    }

    private fun resolveTemplateLines(pattern: String, data: Map<String, Any>): List<String> {
        var result = pattern.replace("\\n", "\n").replace("\r\n", "\n")
        for ((key, value) in data) {
            val strVal = if (value is Double && value % 1.0 == 0.0) {
                value.toLong().toString()
            } else {
                value.toString().replace("\\n", "\n")
            }

            result = result.replace("{$key}", strVal, ignoreCase = true)
        }

        result = result.replace(Regex("\\{[a-zA-Z0-9_]+\\}"), "-")

        return result.split("\n")
    }

    private fun calculateColonTabWidth(lines: List<String>, maxCharsPerLine: Int): Int {
        var maxKeyLen = 0
        val maxAllowedKeyLen = (maxCharsPerLine * 35 / 100).coerceIn(6, 16)
        for (raw in lines) {
            val clean = raw.replace("[C]", "")
                .replace("[B]", "")
                .replace("[BARCODE]", "")
                .replace("[QRCODE]", "")
                .replace("[LINE]", "")
                .trim()

            if (!raw.contains("[BARCODE]") && !raw.contains("[QRCODE]") && !raw.contains("[LINE]") && clean.contains(":")) {
                val keyPart = clean.substringBefore(":").trimEnd()
                if (keyPart.length in 1..maxAllowedKeyLen && keyPart.length > maxKeyLen) {
                    maxKeyLen = keyPart.length
                }
            }
        }

        return maxKeyLen
    }

    private fun formatAndWrapLine(
        cleanLine: String,
        maxCharsPerLine: Int,
        colonTabWidth: Int,
        isCenter: Boolean
    ): List<String> {
        if (!isCenter && colonTabWidth > 0 && cleanLine.contains(":")) {
            val keyPart = cleanLine.substringBefore(":").trimEnd()
            val valPart = cleanLine.substringAfter(":").trimStart()

            if (keyPart.length in 1..colonTabWidth) {
                val paddedKey = keyPart.padEnd(colonTabWidth, ' ')
                val prefix = "$paddedKey : "
                val hangingIndent = " ".repeat(prefix.length)
                val valueMaxChars = (maxCharsPerLine - prefix.length).coerceAtLeast(6)

                return if (valueMaxChars >= 10) {
                    val wrappedValues = wrapRawText(valPart, valueMaxChars)
                    wrappedValues.mapIndexed { index, subVal ->
                        if (index == 0) "$prefix$subVal" else "$hangingIndent$subVal"
                    }
                } else {
                    wrapRawText("$prefix$valPart", maxCharsPerLine)
                }
            }
        }

        return wrapRawText(cleanLine, maxCharsPerLine)
    }

    private fun wrapRawText(text: String, maxCharsPerLine: Int): List<String> {
        if (text.length <= maxCharsPerLine || maxCharsPerLine <= 4) {
            return listOf(text)
        }

        val wrappedLines = mutableListOf<String>()
        val words = text.split(" ")
        var currentLine = StringBuilder()

        for (word in words) {
            if (word.length > maxCharsPerLine) {
                if (currentLine.isNotEmpty()) {
                    wrappedLines.add(currentLine.toString())
                    currentLine = StringBuilder()
                }
                var start = 0
                while (start < word.length) {
                    val end = (start + maxCharsPerLine).coerceAtMost(word.length)
                    wrappedLines.add(word.substring(start, end))
                    start = end
                }
            } else if (currentLine.isEmpty()) {
                currentLine.append(word)
            } else if (currentLine.length + 1 + word.length <= maxCharsPerLine) {
                currentLine.append(" ").append(word)
            } else {
                wrappedLines.add(currentLine.toString())
                currentLine = StringBuilder(word)
            }
        }

        if (currentLine.isNotEmpty()) {
            wrappedLines.add(currentLine.toString())
        }

        return wrappedLines
    }

    private fun buildEscPosFromTemplate(profile: PrinterProfile, lines: List<String>): ByteArray {
        val out = ByteArrayOutputStream()
        val normalCharLimit = calculateAutoMaxChars(
            widthMm = profile.paperWidthMm,
            marginMm = profile.marginMm,
            printMode = "ESCPOS_LABEL",
            isBold = false,
            customOverride = profile.customMaxChars
        )
        val separator = "-".repeat(normalCharLimit)
        val colonTabWidth = calculateColonTabWidth(lines, normalCharLimit)

        out.write(byteArrayOf(0x1B, 0x40))

        for (rawLine in lines) {
            var line = rawLine.trimEnd()
            val isCenter = line.contains("[C]")
            val isBold = line.contains("[B]")
            val isBarcode = line.contains("[BARCODE]")
            val isQrCode = line.contains("[QRCODE]")
            val isLine = line.contains("[LINE]")

            line = line.replace("[C]", "")
                .replace("[B]", "")
                .replace("[BARCODE]", "")
                .replace("[QRCODE]", "")
                .replace("[LINE]", "")
                .trim()

            if (isCenter) {
                out.write(byteArrayOf(0x1B, 0x61, 0x01))
            } else {
                out.write(byteArrayOf(0x1B, 0x61, 0x00))
            }

            if (isLine) {
                out.write("$separator\n".toByteArray(Charset.forName("GBK")))
                continue
            }

            if (isBarcode && line.isNotEmpty() && line != "-") {
                out.write(byteArrayOf(0x1D, 0x48, 0x02))
                out.write(byteArrayOf(0x1D, 0x68, 60))
                out.write(byteArrayOf(0x1D, 0x77, 0x02))

                val codeBytes = "{B$line".toByteArray(Charsets.US_ASCII)
                out.write(byteArrayOf(0x1D, 0x6B, 73, codeBytes.size.toByte()))
                out.write(codeBytes)
                out.write(byteArrayOf(0x0A))
                continue
            }

            if (isQrCode && line.isNotEmpty() && line != "-") {
                val qrBytes = line.toByteArray(Charsets.UTF_8)
                val storeLen = qrBytes.size + 3
                val pL = (storeLen % 256).toByte()
                val pH = (storeLen / 256).toByte()

                out.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x04, 0x00, 0x31, 0x41, 0x32, 0x00))
                out.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43, 0x06))
                out.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x45, 0x31))
                out.write(byteArrayOf(0x1D, 0x28, 0x6B, pL, pH, 0x31, 0x50, 0x30))
                out.write(qrBytes)
                out.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x51, 0x30))
                out.write(byteArrayOf(0x0A))
                continue
            }

            if (isBold) {
                out.write(byteArrayOf(0x1B, 0x45, 0x01))
                out.write(byteArrayOf(0x1D, 0x21, 0x01))
            }

            val wrappedSubLines = formatAndWrapLine(line, normalCharLimit, colonTabWidth, isCenter)
            for (subLine in wrappedSubLines) {
                out.write("$subLine\n".toByteArray(Charset.forName("UTF-8")))
            }

            if (isBold) {
                out.write(byteArrayOf(0x1B, 0x45, 0x00))
                out.write(byteArrayOf(0x1d, 0x21, 0x00))
            }
        }

        out.write(byteArrayOf(0x0A, 0x0A, 0x0A))

        return out.toByteArray()
    }

    private fun buildTsplFromTemplate(profile: PrinterProfile, lines: List<String>): ByteArray {
        val tspl = StringBuilder()
        tspl.append("SIZE ${profile.paperWidthMm} mm, ${profile.labelHeightMm} mm\r\n")
        tspl.append("GAP ${profile.labelGapMm} mm, 0 mm\r\n")
        tspl.append("DIRECTION 1\r\n")
        tspl.append("CLS\r\n")

        var currentY = 20
        val widthDots = profile.paperWidthMm * 8
        val marginDots = (profile.marginMm.coerceAtLeast(1)) * 8
        val usableWidthDots = (widthDots - (marginDots * 2)).coerceAtLeast(120)

        val baseMaxChars = calculateAutoMaxChars(
            widthMm = profile.paperWidthMm,
            marginMm = profile.marginMm,
            printMode = "TSPL_LABEL",
            isBold = false,
            customOverride = profile.customMaxChars
        )
        val colonTabWidth = calculateColonTabWidth(lines, baseMaxChars)

        for (rawLine in lines) {
            var line = rawLine.trimEnd()
            val isCenter = line.contains("[C]")
            val isBold = line.contains("[B}")
            val isBarcode = line.contains("[BARCODE]")
            val isQrCode = line.contains("[QRCODE]")
            val isLine = line.contains("[LINE]")

            line = line.replace("[C]", "")
                .replace("[B]", "")
                .replace("[BARCODE]", "")
                .replace("[QRCODE]", "")
                .replace("[LINE]", "")
                .replace("\"", "'")
                .trim()

            if (isLine) {
                tspl.append("BAR $marginDots,$currentY,$usableWidthDots,3\r\n")
                currentY += 16
                continue
            }

            if (isBarcode && line.isNotEmpty() && line != "-") {
                val barcodeX = if (isCenter) (widthDots / 4).coerceAtLeast(marginDots) else marginDots
                tspl.append("BARCODE $barcodeX,$currentY,\"128\",56,1,0,2,2,\"$line\"\r\n")
                currentY += 85
                continue
            }

            if (isQrCode && line.isNotEmpty() && line != "-") {
                val cellWidth = 5
                val estimatedQrSizeDots = 125
                val qrX = if (isCenter) {
                    ((widthDots - estimatedQrSizeDots) / 2).coerceAtLeast(marginDots)
                } else {
                    marginDots
                }

                tspl.append("QRCODE $qrX,$currentY,M,$cellWidth,A,0,\"$line\"\r\n")
                currentY += estimatedQrSizeDots + 16
                continue
            }

            val font = if (isBold) "3" else "2"
            val effectiveCharDots = if (isBold) 18 else 16
            val maxCharsForLine = calculateAutoMaxChars(
                widthMm = profile.paperWidthMm,
                marginMm = profile.marginMm,
                printMode = "TSPL_LABEL",
                isBold = isBold,
                customOverride = profile.customMaxChars
            )

            val wrappedSubLines = formatAndWrapLine(line, maxCharsForLine, colonTabWidth, isCenter)
            for (subLine in wrappedSubLines) {
                val startX = if (isCenter) {
                    val textWidth = subLine.length * effectiveCharDots
                    ((widthDots - textWidth) / 2).coerceAtLeast(marginDots)
                } else {
                    marginDots
                }

                tspl.append("TEXT $startX,$currentY,\"$font\",0,1,1,\"$subLine\"\r\n")
                currentY += if (isBold) 32 else 26
            }
        }

        tspl.append("PRINT 1,1\r\n")

        return tspl.toString().toByteArray(Charset.forName("UTF-8"))
    }
}