package com.blackboard.services

/** Persistence state of the note, shown in the panel's status line. */
sealed interface SaveStatus {
    /** The note is being read from disk; edits are not saved yet. */
    data object Loading : SaveStatus

    /** Everything in memory is on disk. */
    data object Saved : SaveStatus

    /** There are unsaved changes, or a write is in progress. */
    data object Saving : SaveStatus

    /**
     * The last load or save failed. The in-memory text is preserved.
     * [duringLoad] means the file could not be read, so saving is disabled until a reload succeeds.
     */
    data class Failed(val error: Exception, val duringLoad: Boolean) : SaveStatus

    /** The file changed on disk while there were unsaved edits (detected in a later step). */
    data object Conflict : SaveStatus
}
