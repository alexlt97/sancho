package com.unplugged.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceTimingTest {
    @Test
    fun recordsRecognitionModelAndSpeechDurations() {
        var nowNanos = 0L
        val timing = VoiceTimingRecorder { nowNanos }

        nowNanos = 1_000_000_000L
        timing.markTranscriptPartial()
        nowNanos = 1_500_000_000L
        timing.markTranscriptReceived()
        timing.recordModel(
            VoiceGenerationResult(
                response = "hello",
                modelLoadMillis = 900L,
                timeToFirstTokenMillis = 1_200L,
                generationMillis = 3_400L,
            ),
        )
        nowNanos = 5_000_000_000L
        timing.markSpeechStarted()
        nowNanos = 7_250_000_000L
        timing.markSpeechFinished()

        val report = timing.report()
        assertEquals(1_000L, report.recognitionFirstPartialMillis)
        assertEquals(1_500L, report.recognitionMillis)
        assertEquals(900L, report.modelLoadMillis)
        assertEquals(1_200L, report.modelTtftMillis)
        assertEquals(3_400L, report.modelGenerationMillis)
        assertEquals(2_250L, report.ttsMillis)
        assertEquals(7_250L, report.totalMillis)
    }

    @Test
    fun leavesUnavailablePhasesUnreported() {
        val timing = VoiceTimingRecorder { 2_000_000L }

        val report = timing.report()

        assertNull(report.recognitionFirstPartialMillis)
        assertNull(report.recognitionMillis)
        assertNull(report.modelTtftMillis)
        assertNull(report.modelGenerationMillis)
        assertNull(report.ttsMillis)
    }
}
