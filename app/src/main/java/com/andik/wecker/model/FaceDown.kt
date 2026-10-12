package com.andik.wecker.model

/**
 * Erkennt „Handy mit dem Display nach unten hingelegt“ aus der Schwerkraft entlang der z-Achse
 * (zeigt das Display nach oben, ist sie ≈ +9,8 m/s², nach unten ≈ −9,8).
 *
 * Auslösen nur nach einem Wechsel: Erst muss das Gerät einmal *nicht* nach unten liegen, dann
 * [HOLD_MILLIS] lang nach unten. Liegt es beim Klingeln schon umgedreht da (z. B. nach dem
 * Schlummern), muss man es also erst aufnehmen und wieder umdrehen.
 */
class FaceDownDetector {
    private var armed = false
    private var downSince = -1L

    /** Liefert `true` genau einmal pro Umdrehen. */
    fun onSample(z: Float, timeMillis: Long): Boolean {
        when {
            z > UP_THRESHOLD -> {
                armed = true
                downSince = -1
            }
            z < DOWN_THRESHOLD && armed -> {
                if (downSince < 0) downSince = timeMillis
                if (timeMillis - downSince >= HOLD_MILLIS) {
                    armed = false
                    downSince = -1
                    return true
                }
            }
            else -> downSince = -1 // dazwischen (gekippt, in der Hand): neu zählen
        }
        return false
    }

    companion object {
        /** Deutlich nicht nach unten – auch schräg in der Hand oder auf der Seite. */
        const val UP_THRESHOLD = -3f
        /** Etwa flach mit dem Display nach unten (bis ca. 35° gekippt). */
        const val DOWN_THRESHOLD = -8f
        const val HOLD_MILLIS = 800L
    }
}
