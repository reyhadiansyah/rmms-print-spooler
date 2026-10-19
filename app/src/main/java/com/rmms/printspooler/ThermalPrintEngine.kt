package com.rmms.printspooler

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.UUID

data class PrintPayload(
    val title: String = "",
    val code: String = "",
    val description: String = "",
    val qty: String = "1"
)

class ThermalPrintEngine(private val context: Context) {
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    @SuppressLint("MissingPermission")
    fun getPairedPrinters(): List<BluetoothDevice> {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter: BluetoothAdapter? = bluetoothManager.adapter

        return adapter?.bondedDevices?.toList() ?: emptyList()
    }

    @SuppressLint("MissingPermission")
    suspend fun printData(config: PrinterConfig, payload: PrintPayload): Result<String> = withContext(Dispatchers.IO) {
        if (config.printerMac.isEmpty()) {
            return@withContext Result.failure(Exception("Printer belum dipilih di pengaturan aplikasi."))
        }

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
            ?: return@withContext Result.failure(Exception("Bluetooth tidak didukung pada perangkat ini."))

        if (!adapter.isEnabled) {
            return@withContext Result.failure(Exception("Bluetooth tablet sedang mati. Mohon nyalakan Bluetooth."))
        }

        val device = adapter.getRemoteDevice(config.printerMac)
        var socket = device.createRfcommSocketToServiceRecord(SPP_UUID)

        try {
            adapter.cancelDiscovery()
            socket.connect()
            val outputStream: OutputStream = socket.outputStream

            val commandBytes = if (config.paperType == "LABEL_TSPL") {
                buildTsplLabelCommand(config, payload)
            } else {
                buildEscPosReceiptCommand(config, payload)
            }

            outputStream.write(commandBytes)
            outputStream.flush()

            Thread.sleep(500)
            socket.close()

            Result.success("Cetak berhasil dikirim ke ${config.printerName}")
        } catch (e: Exception) {
            try { socket.close() } catch (_: Exception) {}

            Result.failure(Exception("Gagal terhubung ke printer ${config.printerName}: ${e.message}"))
        }
    }

    private fun buildTsplLabelCommand(config: PrinterConfig, payload: PrintPayload): ByteArray {
        val tspl = StringBuilder()
        tspl.append("SIZE ${config.paperWidthMm} mm, ${config.labelHeightMm} mm\r\n")
        tspl.append("GAP ${config.labelGapMm} mm, 0 mm\r\n")
        tspl.append("DIRECTION 1\r\n")
        tspl.append("CLS\r\n")
        tspl.append("TEXT 24,24,\"3\",0,1,1,\"${payload.title}\"\r\n")
        tspl.append("TEXT 24,64,\"2\",0,1,1,\"${payload.description} (Qty: ${payload.qty})\"\r\n")
        if (payload.code.isNotEmpty()) {
            tspl.append("BARCODE 24,104,\"128\",60,1,0,2,2,\"${payload.code}\"\r\n")
        }
        tspl.append("PRINT 1,1\r\n")

        return tspl.toString().toByteArray(Charset.forName("UTF-8"))
    }

    private fun buildEscPosReceiptCommand(config: PrinterConfig, payload: PrintPayload): ByteArray {
        val escInit = byteArrayOf(0x1B, 0x40)
        val alignCenter = byteArrayOf(0x1B, 0x61, 0x01)
        val alignLeft = byteArrayOf(0x1B, 0x61, 0x00)
        val boldOn = byteArrayOf(0x1B, 0x45, 0x01)
        val boldOff = byteArrayOf(0x1B, 0x45, 0x00)
        val lineFeed = byteArrayOf(0x0A, 0x0A, 0x0A)

        val separator = if (config.paperWidthMm >= 72) "-".repeat(48) else "-".repeat(32)

        val textBuilder = StringBuilder()
        textBuilder.append("${payload.title}\n")
        textBuilder.append("$separator\n")
        textBuilder.append("Kode : ${payload.code}\n")
        textBuilder.append("Ket  : ${payload.description}\n")
        textBuilder.append("Qty  : ${payload.qty}\n")
        textBuilder.append("$separator\n")

        return escInit + alignCenter + boldOn +
                "${payload.title}\n".toByteArray() +
                boldOff + alignLeft +
                "$separator\nKode : ${payload.code}\nKet  : ${payload.description}\nQty  : ${payload.qty}\n$separator\n".toByteArray() +
                lineFeed
    }
}