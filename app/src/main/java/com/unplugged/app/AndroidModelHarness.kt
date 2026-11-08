package com.unplugged.app

import android.content.Context

fun Context.sanchoModelHarness(role: HarnessRole): ModelHarness =
    ModelHarnessFactory.create(role) { path ->
        assets.open(path).bufferedReader().use { it.readText() }
    }
