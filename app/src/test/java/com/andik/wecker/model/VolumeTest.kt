package com.andik.wecker.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.log10

class VolumeTest {
    @Test fun startsQuietEndsFull() {
        assertEquals(0.01f, rampGain(0f), 0.0001f)
        assertEquals(1f, rampGain(1f), 0.0001f)
        assertEquals(1f, rampGain(2f), 0.0001f) // nach Ende der Rampe bleibt es voll
    }

    @Test fun evenStepsInDecibel() {
        // Gleich lange Abschnitte bringen gleich viel Dezibel: je 10 dB pro Viertel.
        val db = listOf(0f, 0.25f, 0.5f, 0.75f, 1f).map { 20 * log10(rampGain(it)) }
        db.zipWithNext().forEach { (a, b) -> assertEquals(10f, b - a, 0.01f) }
    }

    @Test fun monotonic() {
        val gains = (0..100).map { rampGain(it / 100f) }
        assertTrue(gains.zipWithNext().all { (a, b) -> b > a })
    }
}
