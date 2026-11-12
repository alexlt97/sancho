package com.unplugged.app

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityComposerTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val appPackage = instrumentation.targetContext.packageName
    private var activityScenario: ActivityScenario<MainActivity>? = null

    @Before
    fun launchActivity() {
        device.wakeUp()
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        check(device.wait(Until.hasObject(By.pkg(appPackage)), 10_000)) {
            "Sancho did not become visible within 10 seconds"
        }
        device.waitForIdle()
    }

    @After
    fun closeActivity() {
        activityScenario?.close()
        activityScenario = null
    }

    @Test
    fun composerShowsAttachmentMenuAndCompactSendControl() {
        requireVisible(By.descContains("Model status"))
        requireVisible(By.desc("Open settings"))
        check(device.findObject(By.text("Import model")) == null) {
            "Model import must not be visible on the chat surface"
        }
        requireVisible(By.desc("Add attachment"))
        requireVisible(By.desc("Send message"))

        device.findObject(By.desc("Add attachment")).click()

        requireVisible(By.text("Choose image"))
        requireVisible(By.text("Use camera"))
    }

    @Test
    fun settingsShowsModelImportControl() {
        requireVisible(By.desc("Open settings")).click()

        requireVisible(By.text("Settings"))
        requireVisible(By.text("Models")).click()
        requireVisible(By.text("Import model"))
        requireVisible(By.desc("Back")).click()
        requireVisible(By.desc("Back")).click()
        requireVisible(By.desc("Open settings"))
    }

    private fun requireVisible(selector: androidx.test.uiautomator.BySelector) =
        device.wait(Until.findObject(selector), 5_000).also {
            check(it != null) { "UI element was not visible: $selector" }
        }!!
}
