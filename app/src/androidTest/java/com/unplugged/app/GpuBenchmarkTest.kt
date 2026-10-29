package com.unplugged.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.ai.edge.litertlm.Backend
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GpuBenchmarkTest {
    @Test
    fun reportsLocalGpuInferenceTiming() = runBlocking {
        runDeviceBenchmark("gpu", Backend.GPU())
    }
}
