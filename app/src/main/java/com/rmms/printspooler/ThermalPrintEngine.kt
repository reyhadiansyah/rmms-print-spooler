package com.rmms.printspooler

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import kotlinx.coroutines.Dispatchers
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

        val device = adapter.getRemoteDevice(profile.printerMac)
        val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)

        try {
            adapter.cancelDiscovery()
            socket.connect()
            val outputStream: OutputStream = socket.outputStream

            val resolvedLines = resolveTemplateLines(template.bodyPattern, payloadMap)

            val commandBytes = when (profile.printMode) {
                "TSPL_LABEL" -> buildTsplFromTemplate(profile, resolvedLines)
                else -> buildEscPosFromTemplate(profile, resolvedLines)
            }

            outputStream.write(commandBytes)
            outputStream.flush()
            Thread.sleep(600)
            socket.close()

            Result.success("Cetak berhasil (${profile.profileName})")
        } catch (e: Exception) {
            try { socket.close() } catch (_: Exception) {}

            Result.failure(Exception("Gagal mencetak ke ${profile.printerName}: ${e.message}"))
        }
    }

    private fun resolveTemplateLines(pattern: String, data: Map<String, Any>): List<String> {
        var result = pattern
        for ((key, value) in data) {
            val strVal = if (value is Double && value % 1.0 == 0.0) {
                value.toLong().toString()
            } else {
                value.toString()
            }

            result = result.replace("{$key}", strVal, ignoreCase = true)
        }

        result = result.replace(Regex("\\{[a-zA-Z0-9_]+\\}"), "-")

        return result.split("\n")
    }

    private fun buildEscPosFromTemplate(profile: PrinterProfile, lines: List<String>): ByteArray {
        val out = ByteArrayOutputStream()
        val charLimit = if (profile.paperWidthMm >= 72) 48 else 32
        val separator = "-".repeat(charLimit)

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

            out.write("$line\n".toByteArray(Charset.forName("UTF-8")))

            if (isBold) {
                out.write(byteArrayOf(0x1B, 0x45, 0x00))
                out.write(byteArrayOf(0x1d, 0x21, 0x00))
            }
        }

        if (profile.printMode == "ESCPOS_LABEL") {
            out.write(byteArrayOf(0x0A, 0x0A, 0x0A))
        } else {
            out.write(byteArrayOf(0x0A, 0x0A, 0x0A, 0x0A))
        }

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

        for (rawLine in lines) {
            var line = rawLine.trimEnd()
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
                tspl.append("BAR 20,$currentY,${widthDots - 40},3\r\n")
                currentY += 16
                continue
            }

            if (isBarcode && line.isNotEmpty() && line != "-") {
                tspl.append("BARCODE 24,$currentY,\"128\",56,1,0,2,2,\"$line\"\r\n")
                currentY += 85
                continue
            }

            val font = if (isBold) "3" else "2"
            tspl.append("TEXT 24,$currentY,\"$font\",0,1,1,\"$line\"\r\n")
            currentY += if (isBold) 34 else 26
        }

        tspl.append("PRINT 1,1\r\n")

        return tspl.toString().toByteArray(Charset.forName("UTF-8"))
    }
}