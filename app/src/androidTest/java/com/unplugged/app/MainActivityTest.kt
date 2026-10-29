package com.unplugged.app

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @Test
    fun launchesInTheAppProcess() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals("com.unplugged.app", activity.packageName)
            }
        }
    }

    @Test
    fun canOpenTheMainActivityTwice() {
        ActivityScenario.launch(MainActivity::class.java).use { first ->
            first.onActivity { activity ->
                assertEquals(MainActivity::class.java, activity::class.java)
            }
        }
        ActivityScenario.launch(MainActivity::class.java).use { second ->
            second.onActivity { activity ->
                assertEquals(MainActivity::class.java, activity::class.java)
            }
        }
    }
}
