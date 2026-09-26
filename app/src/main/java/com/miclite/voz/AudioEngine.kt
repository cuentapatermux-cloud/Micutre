// Copyright 2026 PollNull

package com.miclite.voz

import android.media.AudioDeviceInfo

data class VoiceEffect(
    val name: String,
    val pitchSemitones: Float = 0f,
    val echoMix: Float = 0f,
    val echoDelayMs: Float = 180f,
    val robotMix: Float = 0f,
    var isPinned: Boolean = false
)

/** Runs microphone input and speaker output through Oboe's low-latency callbacks. */
class AudioEngine {
    @Volatile private var running = false

    fun setGain(value: Float) {
        if (running) nativeSetGain(value.coerceIn(0f, 1f))
    }

    fun start(initialGain: Float, effect: VoiceEffect, preferredOutput: AudioDeviceInfo?, onError: (Int) -> Unit): Boolean {
        if (running) return true
        return try {
            val outputId = preferredOutput?.id ?: 0
            running = nativeStart(
                outputId, initialGain.coerceIn(0f, 1f), effect.pitchSemitones,
                effect.echoMix, effect.echoDelayMs, effect.robotMix
            )
            if (!running) onError(R.string.audio_engine_unavailable)
            running
        } catch (_: UnsatisfiedLinkError) {
            onError(R.string.audio_engine_missing)
            false
        } catch (_: SecurityException) {
            onError(R.string.microphone_permission_needed)
            false
        }
    }

    fun stop() {
        if (running) {
            running = false
            nativeStop()
        }
    }

    fun setEffect(effect: VoiceEffect) {
        nativeSetEffect(effect.pitchSemitones, effect.echoMix, effect.echoDelayMs, effect.robotMix)
    }

    private external fun nativeStart(outputDeviceId: Int, gain: Float, pitchSemitones: Float, echoMix: Float, echoDelayMs: Float, robotMix: Float): Boolean
    private external fun nativeSetGain(gain: Float)
    private external fun nativeSetEffect(pitchSemitones: Float, echoMix: Float, echoDelayMs: Float, robotMix: Float)
    private external fun nativeStop()

    companion object {
        init {
            System.loadLibrary("micutre-audio")
        }
    }
}
