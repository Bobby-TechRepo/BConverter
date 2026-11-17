package io.bsoft.bconverter.feature.editor

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import io.bsoft.bconverter.R
import io.bsoft.bconverter.domain.audio.ClipSound
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ClipSoundPanelTest {
    @get:Rule val rule = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun s(id: Int) = context.getString(id)

    private fun show(sound: ClipSound, set: MutableList<ClipSound> = mutableListOf(), done: () -> Unit = {}) {
        rule.setContent { ClipSoundPanel(sound, 20_000, { set += it }, done) }
    }

    @Test fun the_current_values_are_shown() {
        show(ClipSound(0.5f, 1_500, 2_000))
        rule.onNodeWithText(context.getString(R.string.editor_volume_value, 50)).assertExists()
        rule.onNodeWithText(context.getString(R.string.sound_seconds, 1.5f)).assertExists()
        rule.onNodeWithText(context.getString(R.string.sound_seconds, 2.0f)).assertExists()
    }
    @Test fun reset_is_off_for_untouched_sound() {
        show(ClipSound.Default); rule.onNodeWithText(s(R.string.sound_reset)).assertIsNotEnabled()
    }
    @Test fun reset_applies_the_default() {
        val set = mutableListOf<ClipSound>(); show(ClipSound(0.5f), set)
        rule.onNodeWithText(s(R.string.sound_reset)).assertIsEnabled().performClick()
        assertEquals(listOf(ClipSound.Default), set)
    }
    @Test fun done_closes_the_panel() {
        var closed = false; show(ClipSound.Default, done = { closed = true })
        rule.onNodeWithText(s(R.string.sound_done)).performClick(); assertEquals(true, closed)
    }
}
