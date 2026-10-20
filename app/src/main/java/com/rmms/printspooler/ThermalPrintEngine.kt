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
            return@withContext Result.failure(Exception("Bluetooth device sedang mati."))
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

    private fun wrapTextToWidth(text: String, maxCharsPerLine: Int): List<String> {
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
        val baseCharLimit = if (profile.paperWidthMm >= 72) 48 else 32
        val separator = "-".repeat(baseCharLimit)

        out.write(byteArrayOf(0x1B, 0x40))

        for (rawLine in lines) {
            var line = rawLine.trimEnd()
            val isCenter = line.contains("[C]")
            val isBold = line.contains("[B]")
            val isBarcode = line.contains("[BARCODE]")
            val isLine = line.contains("[LINE]")

            line = line.replace("[C]", "")
                .replace("[B]", "")
                .replace("[BARCODE]", "")
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

            if (isBold) {
                out.write(byteArrayOf(0x1B, 0x45, 0x01))
                out.write(byteArrayOf(0x1D, 0x21, 0x01))
            }

            val wrappedSubLines = wrapTextToWidth(line, baseCharLimit)
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
        val usableWidthDots = (widthDots - 48).coerceAtLeast(160)

        for (rawLine in lines) {
            var line = rawLine.trimEnd()
            val isCenter = line.contains("[C]")
            val isBold = line.contains("[B}")
            val isBarcode = line.contains("[BARCODE]")
            val isLine = line.contains("[LINE]")

            line = line.replace("[C]", "")
                .replace("[B]", "")
                .replace("[BARCODE]", "")
                .replace("[LINE]", "")
                .replace("\"", "'")
                .trim()

            if (isLine) {
                tspl.append("BAR 24,$currentY,$usableWidthDots,3\r\n")
                currentY += 16
                continue
            }

            if (isBarcode && line.isNotEmpty() && line != "-") {
                tspl.append("BARCODE 24,$currentY,\"128\",56,1,0,2,2,\"$line\"\r\n")
                currentY += 85
                continue
            }

            val font = if (isBold) "3" else "2"
            val charWidthDots = if (isBold) 16 else 12
            val maxChars = (usableWidthDots / charWidthDots).coerceAtLeast(10)

            val wrappedSubLines = wrapTextToWidth(line, maxChars)
            for (subLine in wrappedSubLines) {
                val startX = if (isCenter) {
                    val textWidth = subLine.length * charWidthDots
                    ((widthDots - textWidth) / 2).coerceAtLeast(24)
                } else {
                    24
                }

                tspl.append("TEXT $startX,$currentY,\"$font\",0,1,1,\"$subLine\"\r\n")
                currentY += if (isBold) 32 else 24
            }
        }

        tspl.append("PRINT 1,1\r\n")

        return tspl.toString().toByteArray(Charset.forName("UTF-8"))
    }
}