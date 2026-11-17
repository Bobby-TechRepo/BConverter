package io.bsoft.bconverter.domain.timeline

import io.bsoft.bconverter.fakes.TL
import io.bsoft.bconverter.fakes.TL.err
import io.bsoft.bconverter.fakes.TL.ok
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioSpeedTest {
    private val base = TL.withAudio(TL.timeline(TL.video("a", 20_000)), listOf(TL.audio("m", 6_000, start = 1_000), TL.audio("n", 3_000, start = 9_000)))

    @Test fun an_audio_clip_lasts_its_source_span_divided_by_its_speed() {
        assertEquals(3_000L, TL.audio("m", 6_000, speed = 2f).durationMs)
        assertEquals(12_000L, TL.audio("m", 6_000, speed = 0.5f).durationMs)
    }

    @Test fun speed_can_be_changed_on_audio_clips_within_the_allowed_range() {
        val t = ok(TimelineOperations.changeSpeed(base, "m", 2f))
        assertEquals(3_000L, t.audioClips.first { it.id == "m" }.durationMs)
        assertEquals(EditError.InvalidSpeed, err(TimelineOperations.changeSpeed(base, "m", 8f)))
        assertEquals(EditError.InvalidSpeed, err(TimelineOperations.changeSpeed(base, "m", 0.1f)))
    }

    @Test fun slowing_a_clip_into_its_neighbour_is_refused() {
        assertEquals(EditError.AudioOverlap, err(TimelineOperations.changeSpeed(base, "m", 0.5f)))
    }

    @Test fun splitting_a_sped_up_clip_cuts_the_source_where_the_timeline_cut_falls() {
        val fast = ok(TimelineOperations.changeSpeed(base, "m", 2f))   // 1 000..4 000 on the timeline
        val t = ok(TimelineOperations.split(fast, "m", 2_000, "m2"))
        val first = t.audioClips.first { it.id == "m" }; val second = t.audioClips.first { it.id == "m2" }
        assertEquals(2_000L, first.sourceEndMs); assertEquals(2_000L, second.sourceStartMs)
        assertEquals(2f, second.playbackSpeed, 0f); assertEquals(2_000L, second.startTimeMs); assertEquals(4_000L, second.endTimeMs)
    }

    @Test fun playback_maps_the_timeline_time_to_the_source_time_through_the_speed() {
        val fast = ok(TimelineOperations.changeSpeed(base, "m", 2f))
        val active = PreviewMapper.activeAudioAt(fast, 2_000).single { it.clip.id == "m" }
        assertEquals(2_000L, active.sourcePositionMs)   // 1 s into the clip x 2
    }

    @Test fun the_editor_makes_it_undoable() {
        val e = DefaultTimelineEditor(base, io.bsoft.bconverter.fakes.SequentialIds())
        e.changeSpeed("m", 2f); e.undo()
        assertEquals(1f, e.state.value.timeline.audioClips.first { it.id == "m" }.playbackSpeed, 0f)
    }
}
