package com.andik.wecker.model

import kotlin.math.pow

private const val RAMP_START_DB = -40f

/**
 * Verstärkung beim Lauterwerden: von −40 dB (sehr leise) bei [progress] 0 bis 0 dB (volle Weckerlautstärke)
 * bei 1. Der Anstieg ist gleichmäßig in Dezibel, also so, wie das Ohr Lautstärke wahrnimmt.
 */
fun rampGain(progress: Float): Float =
    10f.pow(RAMP_START_DB * (1f - progress.coerceIn(0f, 1f)) / 20f)
