package com.unplugged.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HarnessComparisonBenchmarkTest {
    @Test
    fun comparesChatHarnessWithNoHarness() = runBlocking {
        runHarnessComparisonBenchmark()
    }
}
