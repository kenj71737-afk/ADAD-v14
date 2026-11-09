package com.alad.app.core.audio

import android.os.SystemClock
import android.util.Log

/**
 * TVB sync clock: measures the timing of the ORIGINAL speech so the
 * Vietnamese dub can be fitted to the picture instead of drifting behind it.
 *
 * How the sync works, end to end:
 * 1. Every 10 ms PCM block sent to Gemini passes through [feedPcm]. A simple
 *    RMS voice-activity detector marks where a source utterance starts and
 *    where it ends (450 ms of silence closes it).
 * 2. When a translated segment arrives, [utteranceForSegment] returns the
 *    utterance it belongs to. Its start time + the selected media delay is
 *    the moment the delayed video reaches that line, so the dub must not
 *    start before then ([TvbVoiceDirector] waits for `speakAtMs`).
 * 3. Its duration is the time the dub has to fit into. The director speeds
 *    the TTS rate up (never slower than the actor's pace by much) so a long
 *    Vietnamese line still ends when the character's mouth stops moving.
 * 4. [recordTranslationLatency] keeps a running average of how long Gemini
 *    takes per line. The media delay follows that average instead of being
 *    measured once at the start of the session and then going stale.
 *
 * This is timing alignment, not phoneme lip-sync: Gemini Live does not
 * expose per-word timestamps for the source audio, so no code here can
 * claim frame-accurate lip-sync. What it guarantees is that a dub line
 * starts with its picture and does not run over into the next line.
 */
class DubSyncTracker {

    data class Utterance(val startMs: Long, val endMs: Long) {
        val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
    }

    /** An utterance plus whether its end is known yet. */
    data class TimedUtterance(val utterance: Utterance, val complete: Boolean)

    companion object {
        private const val TAG = "DubSyncTracker"

        /** RMS (16-bit scale) above this counts as speech. Quiet dialogue on a
         * noisy stream can sit near 400-800; 600 keeps soft lines while
         * ignoring most background music/room tone. */
        const val SPEECH_RMS_THRESHOLD = 600f

        /** Silence this long closes an utterance. Shorter gaps are pauses
         * inside one line, not the end of it. */
        const val SILENCE_TO_END_MS = 450L

        /** Utterances shorter than this are treated as noise blips. */
        const val MIN_UTTERANCE_MS = 250L

        /** Safety margin added to the measured translation latency before it
         * becomes the media delay, same 2 s margin the old one-shot
         * measurement used. */
        const val LATENCY_MARGIN_MS = 2000L

        /** A completed utterance stays matchable for this long after it ends;
         * translated text arriving later than this belongs to a newer line. */
        const val UTTERANCE_MATCH_WINDOW_MS = 20000L

        /** A segment arriving this far into an open utterance belongs to
         * that utterance, not to the previous completed line. */
        const val OPEN_UTTERANCE_MATCH_MS = 800L
    }

    private val lock = Any()
    private var utteranceStartMs: Long = -1L
    private var lastSpeechMs: Long = -1L
    private var lastUtterance: Utterance? = null
    private var latencyEmaMs: Long? = null

    fun nowMs(): Long = SystemClock.elapsedRealtime()

    /** Feed one captured PCM block (16 kHz mono, 10 ms) with its capture time. */
    fun feedPcm(pcmData: ByteArray, atMs: Long = nowMs()) {
        if (pcmData.isEmpty()) return
        val rms = AudioUtils.calculateRms(pcmData)
        synchronized(lock) {
            if (rms >= SPEECH_RMS_THRESHOLD) {
                if (utteranceStartMs < 0L) utteranceStartMs = atMs
                lastSpeechMs = atMs
            } else if (utteranceStartMs >= 0L && lastSpeechMs >= 0L &&
                atMs - lastSpeechMs >= SILENCE_TO_END_MS
            ) {
                endCurrentUtteranceLocked()
            }
        }
    }

    /** Close the open utterance now (called on Gemini turnComplete). The
     * utterance ends at the last heard speech, not at the call time. */
    fun endCurrentUtterance(): Utterance? =
        synchronized(lock) { endCurrentUtteranceLocked() }

    private fun endCurrentUtteranceLocked(): Utterance? {
        if (utteranceStartMs < 0L) return null
        val end = maxOf(lastSpeechMs, utteranceStartMs)
        val finished = Utterance(utteranceStartMs, end)
        utteranceStartMs = -1L
        lastSpeechMs = -1L
        if (finished.durationMs >= MIN_UTTERANCE_MS) {
            lastUtterance = finished
            Log.d(TAG, "Source utterance ${finished.durationMs}ms")
            return finished
        }
        return null
    }

    /**
     * The source utterance a translated segment arriving at [arrivalMs]
     * belongs to. Prefers the last completed utterance; while the speaker is
     * still talking it returns the open utterance marked incomplete, so the
     * caller can use its start time for `speakAtMs` but must NOT trust its
     * provisional duration for the TTS rate.
     *
     * The WebAudio tap only forwards non-silent blocks, so during a pause
     * no PCM arrives at all and [feedPcm] cannot see the silence. The gap
     * is therefore also closed lazily here: if the last speech was more
     * than [SILENCE_TO_END_MS] ago, the open utterance is complete.
     */
    fun utteranceForSegment(arrivalMs: Long = nowMs()): TimedUtterance? =
        synchronized(lock) {
            if (utteranceStartMs >= 0L && lastSpeechMs >= 0L &&
                arrivalMs - lastSpeechMs >= SILENCE_TO_END_MS
            ) {
                endCurrentUtteranceLocked()
            }
            val last = lastUtterance
            // A segment arriving well into a new line belongs to that line:
            // Gemini streams partial translations while the speaker is still
            // talking, so preferring the previous completed line here would
            // time a line-2 translation with line-1's picture. Below 800 ms
            // into the new line, the segment more likely belongs to the
            // previous line, whose translation is still catching up.
            if (utteranceStartMs >= 0L) {
                val openStart = utteranceStartMs
                val openEnd = maxOf(lastSpeechMs, openStart)
                if (openEnd - openStart >= OPEN_UTTERANCE_MATCH_MS) {
                    return TimedUtterance(Utterance(openStart, openEnd), complete = false)
                }
            }
            if (last != null && arrivalMs - last.endMs <= UTTERANCE_MATCH_WINDOW_MS) {
                return TimedUtterance(last, complete = true)
            }
            if (utteranceStartMs >= 0L) {
                val start = utteranceStartMs
                val end = maxOf(lastSpeechMs, start)
                return TimedUtterance(Utterance(start, end), complete = false)
            }
            return last?.let { TimedUtterance(it, complete = true) }
        }

    /**
     * Feed one per-line latency sample: time from the end of the source line
     * to the arrival of its translation. Returns the smoothed media delay to
     * use (latency + margin, clamped to the 3-10 s range the video delay
     * pipeline supports). The average moves slowly (30% weight per sample)
     * so one slow line cannot yank the picture delay around.
     */
    fun recordTranslationLatency(latencyMs: Long): Long {
        val sample = (latencyMs.coerceIn(0L, 9000L) + LATENCY_MARGIN_MS)
            .coerceIn(3000L, 10000L)
        synchronized(lock) {
            val ema = latencyEmaMs
            latencyEmaMs = if (ema == null) sample else (ema * 7 + sample * 3) / 10
            return latencyEmaMs!!
        }
    }

    fun reset() = synchronized(lock) {
        utteranceStartMs = -1L
        lastSpeechMs = -1L
        lastUtterance = null
        latencyEmaMs = null
    }
}
