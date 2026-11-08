package com.unplugged.app

import android.content.Context

enum class MoonshineModelVariant(
    val id: String,
    val displayName: String,
    val description: String,
    val cacheDirectoryName: String,
) {
    TINY_STREAMING(
        id = "tiny-streaming",
        displayName = "Tiny Streaming",
        description = "Fastest and smallest; recommended default.",
        cacheDirectoryName = "stt-en-2",
    ),
    SMALL_STREAMING(
        id = "small-streaming",
        displayName = "Small Streaming",
        description = "Better recognition accuracy; uses more resources.",
        cacheDirectoryName = "stt-en-4",
    );

    companion object {
        fun fromId(id: String?): MoonshineModelVariant =
            entries.firstOrNull { it.id == id } ?: TINY_STREAMING
    }
}

class MoonshineSettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun selectedVariant(): MoonshineModelVariant =
        MoonshineModelVariant.fromId(preferences.getString(KEY_VARIANT, null))

    fun setSelectedVariant(variant: MoonshineModelVariant) {
        preferences.edit().putString(KEY_VARIANT, variant.id).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "moonshine_settings"
        const val KEY_VARIANT = "stt_variant"
    }
}
