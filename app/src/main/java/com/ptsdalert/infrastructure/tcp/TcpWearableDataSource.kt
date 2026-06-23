package com.ptsdalert.infrastructure.tcp

import com.ptsdalert.domain.model.PhysiologicalSample
import com.ptsdalert.domain.ports.WearableDataSource
import com.ptsdalert.infrastructure.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.IOException
import java.net.Socket

private const val TAG = "TcpWearableDataSource"

class TcpWearableDataSource(
    // private val host: String = "10.0.2.2", // emulator: host machine alias
    private val host: String = "127.0.0.1",   // physical device: `adb reverse tcp:9999 tcp:9999`
    private val port: Int = 9999
) : WearableDataSource {

    override val deviceLabel: String = "TCP (fake BLE)"

    override fun streamSamples(): Flow<PhysiologicalSample> = flow {
        while (true) {
            try {
                AppLogger.i(TAG, "Connecting to $host:$port")
                Socket(host, port).use { socket ->
                    socket.soTimeout = 5_000
                    AppLogger.i(TAG, "Connected to $host:$port")
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                    var line = reader.readLine()
                    while (line != null) {
                        val sample = parseSample(line)
                        if (sample != null) emit(sample)
                        line = reader.readLine()
                    }
                    AppLogger.w(TAG, "Server closed connection — will retry")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                AppLogger.w(TAG, "Connection lost: ${e.message} — retrying in 1s")
                delay(1_000L)
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun parseSample(json: String): PhysiologicalSample? = try {
        val obj = JSONObject(json)
        PhysiologicalSample(
            timestamp       = obj.optLong("timestamp", System.currentTimeMillis()),
            heartRate       = obj.optInt("heart_rate").takeIf { obj.has("heart_rate") },
            hrv             = obj.optDouble("hrv").takeIf { obj.has("hrv") },
            skinTemperature = obj.optDouble("skin_temperature").takeIf { obj.has("skin_temperature") },
            stressScore     = obj.optInt("stress_score").takeIf { obj.has("stress_score") }
        )
    } catch (e: Exception) {
        AppLogger.e(TAG, "Failed to parse sample: $json — ${e.message}")
        null
    }
}
