package com.andik.wecker.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceDownTest {
    private fun FaceDownDetector.feed(z: Float, from: Long, to: Long): Boolean =
        (from..to step 100).map { onSample(z, it) }.any { it }

    @Test fun `turning over after lying face up snoozes`() {
        val d = FaceDownDetector()
        assertFalse(d.feed(9.8f, 0, 500))
        assertTrue(d.feed(-9.7f, 600, 1500))
    }

    @Test fun `short flip does not snooze`() {
        val d = FaceDownDetector()
        d.feed(9.8f, 0, 300)
        assertFalse(d.feed(-9.7f, 400, 1000))
        assertFalse(d.feed(9.8f, 1100, 1200))
    }

    @Test fun `already face down when ringing starts needs a pick up first`() {
        val d = FaceDownDetector()
        assertFalse(d.feed(-9.8f, 0, 5000))
        d.feed(5f, 5100, 5300)
        assertTrue(d.feed(-9.8f, 5400, 6400))
    }

    @Test fun `snoozes only once per turn`() {
        val d = FaceDownDetector()
        d.feed(9.8f, 0, 100)
        assertTrue(d.feed(-9.8f, 200, 1200))
        assertFalse(d.feed(-9.8f, 1300, 5000))
    }
}
