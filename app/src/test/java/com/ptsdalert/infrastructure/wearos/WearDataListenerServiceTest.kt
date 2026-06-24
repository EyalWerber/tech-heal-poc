package com.ptsdalert.infrastructure.wearos

import org.junit.Assert.*
import org.junit.Test

class WearDataListenerServiceTest {

    @Test fun `parseSample full valid JSON returns correct sample`() {
        val json = """{"timestamp":1000,"heart_rate":85,"hrv":27.5,"skin_temperature":36.5,"stress_score":14}"""
        val sample = WearDataListenerService.parseSample(json)
        assertNotNull(sample)
        assertEquals(1000L, sample!!.timestamp)
        assertEquals(85, sample.heartRate)
        assertEquals(27.5, sample.hrv!!, 0.001)
        assertEquals(36.5, sample.skinTemperature!!, 0.001)
        assertEquals(14, sample.stressScore)
    }

    @Test fun `parseSample missing optional fields returns nulls`() {
        val json = """{"timestamp":2000}"""
        val sample = WearDataListenerService.parseSample(json)
        assertNotNull(sample)
        assertEquals(2000L, sample!!.timestamp)
        assertNull(sample.heartRate)
        assertNull(sample.hrv)
        assertNull(sample.skinTemperature)
        assertNull(sample.stressScore)
    }

    @Test fun `parseSample missing timestamp defaults to non-zero`() {
        val json = """{"heart_rate":72}"""
        val sample = WearDataListenerService.parseSample(json)
        assertNotNull(sample)
        assertTrue(sample!!.timestamp > 0)
    }

    @Test fun `parseSample malformed JSON returns null`() {
        assertNull(WearDataListenerService.parseSample("not-json"))
    }

    @Test fun `parseSample empty string returns null`() {
        assertNull(WearDataListenerService.parseSample(""))
    }

    @Test fun `parseSample skin_temperature present is mapped`() {
        val json = """{"timestamp":3000,"skin_temperature":37.1}"""
        val sample = WearDataListenerService.parseSample(json)
        assertNotNull(sample)
        assertEquals(37.1, sample!!.skinTemperature!!, 0.001)
    }
}
