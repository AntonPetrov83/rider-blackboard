package com.blackboard.services

import com.blackboard.storage.NoteStorage
import com.blackboard.storage.ReadResult
import com.blackboard.storage.WriteResult
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

/**
 * Note state and save coordination, independent of the IDE so it can be tested with a fake storage and a test scope.
 *
 * - The text lives in memory; every change bumps [revision].
 * - Nothing is written until [load] finished successfully, so a non-empty file is never replaced
 *   by the empty initial text. Edits made before the load completes are replaced by the loaded content
 *   (the editor is read-only until then).
 * - Saves are debounced and strictly sequential (single [writeMutex]); each write takes the latest snapshot
 *   inside the mutex, so an older revision can never overwrite a newer one.
 * - A failed write keeps the text in memory and reports [SaveStatus.Failed]; [retrySave] tries again.
 *
 * All public methods are thread-safe and non-blocking, except [flushBlocking].
 */
class BlackboardCore(
    private val storageFactory: () -> NoteStorage,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val debounceMs: Long = DEFAULT_DEBOUNCE_MS,
) {
    private val lock = Any()
    private val writeMutex = Mutex()

    // Guarded by lock.
    private var text = ""
    private var revision = 0L
    private var savedRevision = 0L
    private var loaded = false
    private var storage: NoteStorage? = null
    private var loadJob: Job? = null
    private var debounceJob: Job? = null

    private val _status = MutableStateFlow<SaveStatus>(SaveStatus.Loading)
    val status: StateFlow<SaveStatus> = _status.asStateFlow()

    val isLoaded: Boolean get() = synchronized(lock) { loaded }

    /** Monotonic counter of text changes (user edits and loads). */
    val currentRevision: Long get() = synchronized(lock) { revision }

    fun currentText(): String = synchronized(lock) { text }

    /** Reads the note on [io]. Does nothing if already loaded or loading. */
    fun load() {
        synchronized(lock) {
            if (loaded || loadJob?.isActive == true) return
            _status.value = SaveStatus.Loading
            loadJob = scope.launch(io) { doLoad() }
        }
    }

    private fun doLoad() {
        val result = try {
            val s = synchronized(lock) { storage } ?: storageFactory().also { synchronized(lock) { storage = it } }
            s.read()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LOG.warn("Failed to initialize Blackboard storage", e)
            ReadResult.Failure(e as? IOException ?: IOException(e))
        }
        synchronized(lock) {
            when (result) {
                is ReadResult.Content -> applyLoaded(result.text)
                ReadResult.Missing -> applyLoaded("")
                is ReadResult.Failure -> _status.value = SaveStatus.Failed(result.error, duringLoad = true)
            }
        }
    }

    private fun applyLoaded(loadedText: String) {
        text = loadedText
        revision++
        savedRevision = revision
        loaded = true
        _status.value = SaveStatus.Saved
    }

    /** Records an edit and schedules a debounced save. Identical text is ignored. */
    fun onTextChanged(newText: String) {
        synchronized(lock) {
            if (newText == text) return
            text = newText
            revision++
            if (!loaded) return
            if (_status.value == SaveStatus.Saved) _status.value = SaveStatus.Saving
            debounceJob?.cancel()
            debounceJob = scope.launch {
                delay(debounceMs)
                saveLatest()
            }
        }
    }

    /** Retries after a failure: reloads if the initial load failed, otherwise saves the latest text. */
    fun retrySave() {
        if (isLoaded) scope.launch { saveLatest() } else load()
    }

    /** Saves unsaved changes immediately, skipping the debounce delay. */
    suspend fun flush() {
        synchronized(lock) {
            debounceJob?.cancel()
            debounceJob = null
        }
        saveLatest()
    }

    /**
     * Blocking [flush] for disposal. Does not use [scope], which may already be cancelled.
     * Returns false if unsaved changes could not be written within [timeoutMs].
     */
    fun flushBlocking(timeoutMs: Long): Boolean {
        val finished = runBlocking { withTimeoutOrNull(timeoutMs) { flush(); true } } ?: false
        val clean = finished && hasNoUnsavedChanges()
        if (!clean) LOG.warn("Blackboard: unsaved changes could not be written on close (timeout=$timeoutMs ms)")
        return clean
    }

    private fun hasNoUnsavedChanges(): Boolean = synchronized(lock) { !loaded || revision == savedRevision }

    private suspend fun saveLatest() {
        writeMutex.withLock {
            // Once the write started it must complete and record its result, even if the caller is cancelled.
            withContext(NonCancellable) { writeLatestSnapshot() }
        }
    }

    private suspend fun writeLatestSnapshot() {
        val (snapshot, snapshotRevision, s) = synchronized(lock) {
            if (!loaded || revision == savedRevision) return
            _status.value = SaveStatus.Saving
            Triple(text, revision, storage!!)
        }
        val result = withContext(io) { s.write(snapshot) }
        synchronized(lock) {
            when (result) {
                is WriteResult.Success -> {
                    savedRevision = snapshotRevision
                    _status.value = if (revision == savedRevision) SaveStatus.Saved else SaveStatus.Saving
                }
                is WriteResult.Failure -> _status.value = SaveStatus.Failed(result.error, duringLoad = false)
            }
        }
    }

    companion object {
        private val LOG = logger<BlackboardCore>()

        const val DEFAULT_DEBOUNCE_MS = 500L
    }
}
