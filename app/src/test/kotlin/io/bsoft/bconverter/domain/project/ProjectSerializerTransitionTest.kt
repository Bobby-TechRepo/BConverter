package io.bsoft.bconverter.domain.project

import io.bsoft.bconverter.domain.timeline.Timeline
import io.bsoft.bconverter.domain.timeline.Transition
import io.bsoft.bconverter.domain.timeline.TransitionType
import io.bsoft.bconverter.domain.timeline.VideoProject
import io.bsoft.bconverter.fakes.TL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProjectSerializerTransitionTest {
    private fun stored(t: Timeline) = StoredProject(VideoProject("p", "P", t, TL.exportSettings()), 1_000, 2_000)
    private fun roundTrip(s: StoredProject) = (ProjectSerializer.decode(ProjectSerializer.encode(s)) as DecodeResult.Decoded).stored

    @Test fun transitions_round_trip() {
        val t = Timeline(
            listOf(
                TL.video("a", 10_000),
                TL.video("b", 10_000).copy(transitionIn = Transition(TransitionType.CROSSFADE, 1_250)),
                TL.video("c", 10_000).copy(transitionIn = Transition(TransitionType.FADE_BLACK, 750)),
            ),
        ).normalized()
        assertEquals(stored(t), roundTrip(stored(t)))
        assertEquals(Transition(TransitionType.CROSSFADE, 1_250), roundTrip(stored(t)).project.timeline.videoClips[1].transitionIn)
    }

    @Test fun a_project_saved_before_transitions_existed_opens_with_plain_cuts() {
        val json = ProjectSerializer.encode(stored(TL.timeline(TL.video("a"), TL.video("b"))))
        assertFalse("plain cuts are not written", json.contains("transition"))
        val opened = (ProjectSerializer.decode(json) as DecodeResult.Decoded).stored.project.timeline
        assertTrue(opened.videoClips.all { it.transitionIn.isNone })
    }

    @Test fun an_unknown_transition_type_is_reported_as_a_damaged_project() {
        val good = ProjectSerializer.encode(stored(Timeline(listOf(TL.video("a"), TL.video("b").copy(transitionIn = Transition(TransitionType.CROSSFADE, 500))))))
        assertTrue(ProjectSerializer.decode(good.replace("CROSSFADE", "SPIN")) is DecodeResult.Corrupt)
    }

    @Test fun a_transition_length_outside_the_allowed_range_is_reported_as_a_damaged_project() {
        val good = ProjectSerializer.encode(stored(Timeline(listOf(TL.video("a"), TL.video("b").copy(transitionIn = Transition(TransitionType.CROSSFADE, 500))))))
        assertTrue(ProjectSerializer.decode(good.replace("\"durationMs\":500", "\"durationMs\":99999")) is DecodeResult.Corrupt)
    }

    @Test fun the_muted_flag_round_trips_and_older_projects_are_unmuted() {
        val t = Timeline(listOf(TL.video("a", 10_000, muted = true), TL.video("b", 10_000))).normalized()
        val back = roundTrip(stored(t)).project.timeline.videoClips
        assertTrue(back[0].audioMuted); assertFalse(back[1].audioMuted)
    }
}
