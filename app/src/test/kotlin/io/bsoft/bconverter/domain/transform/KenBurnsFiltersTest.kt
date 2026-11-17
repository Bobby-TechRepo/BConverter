package io.bsoft.bconverter.domain.transform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KenBurnsFiltersTest {
    private fun build(k: KenBurns, w: Int = 640, h: Int = 360, ms: Long = 2000, reversed: Boolean = false, fps: Double = 25.0) =
        KenBurnsFilters.build(k, w, h, ms, reversed, fps)

    @Test fun no_motion_adds_no_filters() = assertEquals(emptyList<String>(), build(KenBurns.None))

    @Test fun a_picture_of_unknown_size_or_rate_gets_no_motion() {
        assertEquals(emptyList<String>(), build(KenBurns(KenBurnsMode.ZOOM_IN), w = 0, h = 0))
        assertEquals(emptyList<String>(), build(KenBurns(KenBurnsMode.ZOOM_IN), fps = 0.0))
    }

    @Test fun a_zoom_is_one_zoompan_that_keeps_the_frame_size() {
        val f = build(KenBurns(KenBurnsMode.ZOOM_IN, 1.5f))
        assertEquals(listOf("setpts=PTS-STARTPTS", "fps=25", "scale=1280:720:flags=bilinear"), f.take(3))
        assertEquals("zoompan=z='(1+0.5*clip(on/50,0,1))':x='clip(0.5*iw-iw/(1+0.5*clip(on/50,0,1))/2,0,iw-iw/(1+0.5*clip(on/50,0,1)))':" +
            "y='clip(0.5*ih-ih/(1+0.5*clip(on/50,0,1))/2,0,ih-ih/(1+0.5*clip(on/50,0,1)))':d=1:s=640x360:fps=25", f[3])
    }

    @Test fun a_pan_keeps_its_zoom() {
        val z = build(KenBurns(KenBurnsMode.PAN_LEFT, 1.5f)).last()
        assertTrue(z, z.startsWith("zoompan=z='1.5':x='clip((0.6667-0.3333*clip(on/50,0,1))*iw-iw/1.5/2,0,iw-iw/1.5)'"))
        assertTrue(z, z.contains(":y='clip(0.5*ih-ih/1.5/2,0,ih-ih/1.5)':d=1:s=640x360:fps=25"))
    }

    @Test fun a_reversed_clip_gets_its_motion_written_backwards() {
        val forward = build(KenBurns(KenBurnsMode.ZOOM_OUT, 1.5f)).last()
        val reversed = build(KenBurns(KenBurnsMode.ZOOM_OUT, 1.5f), reversed = true).last()
        assertTrue(forward.startsWith("zoompan=z='(1.5-0.5*clip(on/50,0,1))'"))
        assertTrue(reversed.startsWith("zoompan=z='(1+0.5*clip(on/50,0,1))'"))
    }

    @Test fun big_pictures_are_not_scaled_up_first() {
        val f = build(KenBurns(KenBurnsMode.ZOOM_IN), w = 3840, h = 2160)
        assertEquals(false, f.any { it.startsWith("scale") })
        assertEquals(true, build(KenBurns(KenBurnsMode.ZOOM_IN), w = 1920, h = 1080).contains("scale=3840:2160:flags=bilinear"))
    }

    @Test fun odd_sizes_are_made_even_and_very_short_clips_do_not_divide_by_zero() {
        val f = build(KenBurns(KenBurnsMode.ZOOM_IN, 1.5f), w = 641, h = 361, ms = 1)
        assertTrue(f.last(), f.last().contains(":s=640x360:"))
        assertTrue(f.last(), f.last().contains("clip(on/1,0,1)"))
    }

    @Test fun only_listed_filters_are_used() {
        for (mode in KenBurnsMode.entries) build(KenBurns(mode)).forEach { assertTrue(it, it.substringBefore('=') in KenBurnsFilters.USED_FILTERS) }
    }
}
