package io.bsoft.bconverter.media.ffmpeg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BaseFontPickerTest {
    @Test fun the_plain_roboto_file_is_preferred() =
        assertEquals("Roboto-Regular.ttf", BaseFontPicker.pick(listOf("NotoSansCJK-Regular.ttc", "Roboto-Bold.ttf", "Roboto-Regular.ttf", "RobotoCondensed-Regular.ttf")))

    @Test fun the_variable_roboto_file_is_accepted() =
        assertEquals("Roboto[wdth,wght].ttf", BaseFontPicker.pick(listOf("Roboto[wdth,wght].ttf", "RobotoFlex-Regular.ttf", "Clock.ttf")))

    @Test fun other_sans_fonts_are_the_fallback() {
        assertEquals("DroidSans.ttf", BaseFontPicker.pick(listOf("DroidSansMono.ttf", "DroidSans.ttf", "Foo.ttf")))
        assertEquals("NotoSans-Regular.ttf", BaseFontPicker.pick(listOf("NotoSans-Regular.ttf", "NotoSansCJK-Regular.ttc")))
    }

    @Test fun nothing_suitable_gives_null() = assertNull(BaseFontPicker.pick(listOf("Clock.ttf", "Emoji.ttf")))
}
