package com.blackboard.services

/** Where the user was in the note: caret [caretOffset] and the editor's vertical scroll in pixels. */
data class EditorViewState(val caretOffset: Int, val verticalScrollOffset: Int)
