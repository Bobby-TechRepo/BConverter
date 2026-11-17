package io.bsoft.bconverter.domain.project

import io.bsoft.bconverter.domain.model.MediaSource
import io.bsoft.bconverter.domain.timeline.Timeline
import io.bsoft.bconverter.fakes.FakeProjectRepository
import io.bsoft.bconverter.fakes.TL
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reopening after "kill application → restart" (spec §41) and handling of missing sources (spec §40, §46). */
class ProjectOpenerTest {
    private val repo = FakeProjectRepository()
    private val missingSources = mutableSetOf<MediaSource>()
    private val opener = ProjectOpener(repo) { it !in missingSources }

    private val timeline = TL.withAudio(
        TL.timeline(TL.video("v1"), TL.video("v2")),
        listOf(TL.audio("m1")),
    )
    private val project = TL.project(timeline)

    @Test fun an_autosaved_project_is_recovered_by_a_fresh_opener() = runBlocking {
        ProjectAutosaver(repo) { 10 }.save(project)       // "before the app was killed"
        val result = ProjectOpener(repo) { true }.open("p1") // "after restart": all new objects
        val opened = result as OpenResult.Opened
        assertEquals(timeline, opened.stored.project.timeline)
        assertTrue(opened.missing.isEmpty())
    }

    @Test fun an_unknown_id_is_not_found() = runBlocking {
        assertEquals(OpenResult.NotFound, opener.open("nope"))
    }

    @Test fun a_damaged_project_is_reported_as_corrupt() = runBlocking {
        ProjectAutosaver(repo) { 10 }.save(project)
        repo.corrupt("p1")
        assertEquals(OpenResult.Corrupt, opener.open("p1"))
    }

    @Test fun a_missing_source_is_reported_with_the_clips_that_use_it() = runBlocking {
        ProjectAutosaver(repo) { 10 }.save(project)
        missingSources += TL.source("v2")
        val opened = opener.open("p1") as OpenResult.Opened
        assertEquals(listOf(MissingSource(TL.source("v2"), listOf("v2"))), opened.missing)
    }

    @Test fun the_project_still_opens_when_a_source_is_missing() = runBlocking {
        ProjectAutosaver(repo) { 10 }.save(project)
        missingSources += TL.source("m1")
        val opened = opener.open("p1") as OpenResult.Opened
        assertEquals(timeline, opened.stored.project.timeline)
        assertEquals(listOf("m1"), opened.missing.single().clipIds)
    }

    @Test fun one_source_used_by_several_clips_is_reported_once() = runBlocking {
        val shared = TL.source("shared.mp4")
        val clips = listOf("a", "b").map {
            io.bsoft.bconverter.domain.timeline.VideoClip(it, shared, 10_000, 0, 10_000)
        }
        ProjectAutosaver(repo) { 10 }.save(project.copy(timeline = Timeline(videoClips = clips).normalized()))
        missingSources += shared
        val opened = opener.open("p1") as OpenResult.Opened
        assertEquals(1, opened.missing.size)
        assertEquals(listOf("a", "b"), opened.missing.single().clipIds)
    }

    @Test fun missing_media_check_works_on_any_timeline() {
        missingSources += TL.source("v1")
        assertEquals(listOf("v1"), MissingMediaChecker { it !in missingSources }.check(timeline).single().clipIds)
    }

    @Test fun a_missing_picture_in_picture_source_is_reported() {
        val pipSource = TL.source("pp")
        val withPip = timeline.copy(pipClips = listOf(io.bsoft.bconverter.domain.timeline.PipClip("pp", pipSource, 5_000, 0, 3_000, 1_000)))
        missingSources += pipSource
        val missing = MissingMediaChecker { it !in missingSources }.check(withPip)
        assertEquals(listOf("pp"), missing.single().clipIds)
        assertEquals(pipSource, missing.single().source)
    }
}

class ProjectListTest {
    private val repo = FakeProjectRepository()

    @Test fun summaries_list_the_most_recently_modified_first() = runBlocking {
        var now = 100L
        val a = ProjectAutosaver(repo) { now }
        a.save(TL.project(TL.timeline(TL.video("v1"))).copy(id = "old"))
        now = 900
        ProjectAutosaver(repo) { now }.save(TL.project(TL.timeline(TL.video("v1"))).copy(id = "new"))
        assertEquals(listOf("new", "old"), repo.list().map { it.id })
    }

    @Test fun a_summary_shows_name_clip_count_and_duration() = runBlocking {
        ProjectAutosaver(repo) { 5 }.save(
            TL.project(TL.withAudio(TL.timeline(TL.video("v1", 10_000), TL.video("v2", 5_000)), listOf(TL.audio("m1")))),
        )
        val s = repo.list().single()
        assertEquals("Project", s.name)
        assertEquals(3, s.clipCount)
        assertEquals(15_000, s.durationMs)
        assertEquals(5, s.modifiedAtMs)
    }

    @Test fun deleting_a_project_removes_it_from_the_list() = runBlocking {
        ProjectAutosaver(repo) { 1 }.save(TL.project(TL.timeline(TL.video("v1"))))
        repo.delete("p1")
        assertTrue(repo.list().isEmpty())
        assertEquals(ProjectLoadResult.NotFound, repo.load("p1"))
    }
}
