package io.bsoft.bconverter.domain.project

import android.net.Uri
import io.bsoft.bconverter.domain.model.AudioCodec
import io.bsoft.bconverter.domain.model.AudioSettings
import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.MediaSource
import io.bsoft.bconverter.domain.model.Resolution
import io.bsoft.bconverter.domain.model.VideoCodec
import io.bsoft.bconverter.domain.model.VideoQuality
import io.bsoft.bconverter.domain.model.VideoSettings
import io.bsoft.bconverter.domain.timeline.AudioClip
import io.bsoft.bconverter.domain.timeline.AudioTrack
import io.bsoft.bconverter.domain.timeline.ExportSettings
import io.bsoft.bconverter.domain.timeline.Timeline
import io.bsoft.bconverter.domain.timeline.VideoClip
import io.bsoft.bconverter.domain.timeline.VideoProject
import io.bsoft.bconverter.fakes.TL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProjectSerializerTest {
    private val uri = MediaSource.UriSource(Uri.parse("content://media/external/video/42"))

    private fun stored(timeline: Timeline, export: ExportSettings = TL.exportSettings(), id: String = "p-1") =
        StoredProject(VideoProject(id, "My project", timeline, export), createdAtMs = 1_000, modifiedAtMs = 2_000)

    private fun roundTrip(s: StoredProject): StoredProject =
        (ProjectSerializer.decode(ProjectSerializer.encode(s)) as DecodeResult.Decoded).stored

    private fun corrupt(json: String): DecodeResult.Corrupt = ProjectSerializer.decode(json) as DecodeResult.Corrupt

    // ---- round trips ----
    @Test fun an_empty_project_round_trips() {
        val s = stored(Timeline())
        assertEquals(s, roundTrip(s))
    }

    @Test fun id_name_and_timestamps_round_trip() {
        val r = roundTrip(stored(Timeline(), id = "abc"))
        assertEquals("abc", r.project.id)
        assertEquals("My project", r.project.name)
        assertEquals(1_000, r.createdAtMs)
        assertEquals(2_000, r.modifiedAtMs)
    }

    @Test fun video_clips_keep_source_range_speed_and_order() {
        val clip = VideoClip("v1", uri, sourceDurationMs = 60_000, sourceStartMs = 5_000, sourceEndMs = 25_000, playbackSpeed = 0.25f)
        val s = stored(Timeline(videoClips = listOf(clip, TL.video("v2", spanMs = 3_000, speed = 4f))).normalized())
        val r = roundTrip(s)
        assertEquals(s.project.timeline, r.project.timeline)
        assertEquals(listOf("v1", "v2"), r.project.timeline.videoClips.map { it.id })
        assertEquals(0.25f, r.project.timeline.videoClips[0].playbackSpeed, 0f)
    }

    @Test fun content_uri_and_file_sources_both_round_trip() {
        val s = stored(Timeline(videoClips = listOf(
            VideoClip("v1", uri, 10_000, 0, 10_000),
            TL.video("v2"),
        )).normalized())
        val sources = roundTrip(s).project.timeline.videoClips.map { it.source }
        assertEquals(uri, sources[0])
        assertEquals(TL.source("v2"), sources[1])
    }

    @Test fun audio_tracks_clips_volume_and_start_times_round_trip() {
        val timeline = TL.withAudio(
            TL.timeline(TL.video("v1")),
            listOf(TL.audio("m1", start = 0, volume = 0.5f), TL.audio("m2", start = 6_000, volume = 2f)),
            listOf(AudioClip("m3", uri, 9_000, 1_000, 4_000, 1f, 2_500)),
        )
        val r = roundTrip(stored(timeline))
        assertEquals(timeline, r.project.timeline)
        assertEquals(listOf("lane-0", "lane-1"), r.project.timeline.audioTracks.map { it.id })
    }

    @Test fun export_settings_with_crf_round_trip() {
        val export = ExportSettings(
            ContainerFormat.MKV,
            VideoSettings(VideoCodec.VP9, VideoQuality.Crf(31), Resolution(1280, 720), 29.97, "main", "4.1", "yuv420p"),
            AudioSettings(AudioCodec.OPUS, 96, 48_000, 2),
            keepOriginalAudio = false,
        )
        assertEquals(export, roundTrip(stored(Timeline(), export)).project.export)
    }

    @Test fun export_settings_with_bitrate_and_no_audio_round_trip() {
        val export = ExportSettings(ContainerFormat.MP4, VideoSettings(VideoCodec.H264, VideoQuality.Bitrate(4_000)), audio = null)
        assertEquals(export, roundTrip(stored(Timeline(), export)).project.export)
    }

    @Test fun source_metadata_is_not_persisted_because_it_is_probed_again() {
        val project = TL.project(TL.timeline(TL.video("v1")))
        assertTrue(project.sourceMetadata.isNotEmpty())
        val r = roundTrip(StoredProject(project, 1, 2))
        assertTrue(r.project.sourceMetadata.isEmpty())
    }

    @Test fun names_with_quotes_and_unicode_round_trip() {
        val s = stored(Timeline()).let { it.copy(project = it.project.copy(name = "Beach \"day\" ☀ \\ 2026")) }
        assertEquals("Beach \"day\" ☀ \\ 2026", roundTrip(s).project.name)
    }

    // ---- damaged data is reported, never thrown ----
    @Test fun text_that_is_not_json_is_corrupt() { corrupt("not json at all") }
    @Test fun an_empty_string_is_corrupt() { corrupt("") }
    @Test fun a_truncated_document_is_corrupt() {
        val json = ProjectSerializer.encode(stored(TL.timeline(TL.video("v1"))))
        corrupt(json.substring(0, json.length / 2))
    }

    @Test fun an_unsupported_version_is_corrupt() {
        val json = ProjectSerializer.encode(stored(Timeline())).replace("\"version\":1", "\"version\":999")
        corrupt(json)
    }

    @Test fun a_missing_required_field_is_corrupt() {
        val json = ProjectSerializer.encode(stored(Timeline())).replace("\"name\"", "\"nom\"")
        corrupt(json)
    }

    @Test fun an_unknown_codec_name_is_corrupt() {
        val json = ProjectSerializer.encode(stored(Timeline())).replace("\"H264\"", "\"BOGUS\"")
        corrupt(json)
    }

    @Test fun a_clip_whose_range_exceeds_its_source_is_corrupt() {
        val json = ProjectSerializer.encode(stored(TL.timeline(TL.video("v1", spanMs = 10_000))))
            .replace("\"sourceEndMs\":10000", "\"sourceEndMs\":99999")
        corrupt(json)
    }
}
