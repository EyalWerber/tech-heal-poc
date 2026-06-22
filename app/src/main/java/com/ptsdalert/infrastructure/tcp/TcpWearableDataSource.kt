package com.ptsdalert.infrastructure.tcp

import com.ptsdalert.domain.model.PhysiologicalSample
import com.ptsdalert.domain.ports.WearableDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Socket

// Connects to a TCP server on the host machine (10.0.2.2 is the emulator's alias for localhost)
// and reads newline-delimited JSON samples sent by the Python script.
//
// JSON format expected from Python:
//   {"timestamp": 1234567890, "heart_rate": 80, "hrv": 40.0, "skin_temperature": 36.6, "stress_score": 30}
class TcpWearableDataSource(
    // private val host: String = "10.0.2.2", // emulator: host machine alias
    private val host: String = "127.0.0.1",   // physical device: use `adb reverse tcp:9999 tcp:9999` to tunnel via USB
    private val port: Int = 9999
) : WearableDataSource {

    override val deviceLabel: String = "TCP (fake BLE)"

    override fun streamSamples(): Flow<PhysiologicalSample> = flow {
        while (true) {
            try {
                Socket(host, port).use { socket ->
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                    var line = reader.readLine()
                    while (line != null) {
                        val sample = parseSample(line)
                        if (sample != null) emit(sample)
                        line = reader.readLine()
                    }
                }
            } catch (e: Exception) {
                // Server not running yet — retry after 2 seconds
                delay(2_000L)
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
        null
    }
}
