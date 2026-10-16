package io.bsoft.bconverter.domain.timeline

import java.util.UUID

sealed interface EditError {
    data class ClipNotFound(val clipId: String) : EditError
    data object SplitOutsideClip : EditError
    data object InvalidTrimRange : EditError
    data object InvalidSpeed : EditError
    data object InvalidVolume : EditError
    /** e.g. changing the speed of an audio clip or the volume of a video clip. */
    data object WrongClipType : EditError
    data object InvalidIndex : EditError
    data object InvalidStartTime : EditError
    data object AudioOverlap : EditError
    data object DuplicateId : EditError
    data object InvalidSourceDuration : EditError
    data object NothingSelected : EditError
}

sealed interface EditResult {
    data class Success(val timeline: Timeline) : EditResult
    data class Failure(val error: EditError) : EditResult
}

fun interface IdGenerator { fun next(): String }

object UuidIdGenerator : IdGenerator { override fun next(): String = UUID.randomUUID().toString() }
