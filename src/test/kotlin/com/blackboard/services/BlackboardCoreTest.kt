package com.blackboard.services

import com.blackboard.storage.FileVersion
import com.blackboard.storage.NoteStorage
import com.blackboard.storage.ReadResult
import com.blackboard.storage.WriteResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.Collections

@OptIn(ExperimentalCoroutinesApi::class)
class BlackboardCoreTest {

    private class FakeStorage(private val initial: ReadResult = ReadResult.Missing) : NoteStorage {
        val writes: MutableList<String> = Collections.synchronizedList(mutableListOf())

        @Volatile
        var failWrites = false

        /** When set, the next write waits until it is completed. */
        @Volatile
        var gate: CompletableDeferred<Unit>? = null

        @Volatile
        var reads = 0

        override fun read(): ReadResult {
            reads++
            return initial
        }

        override fun write(text: String): WriteResult {
            gate?.let { g -> gate = null; runBlocking { g.await() } }
            if (failWrites) return WriteResult.Failure(IOException("disk full"))
            writes += text
            return WriteResult.Success(FileVersion(0, text.hashCode().toString()))
        }
    }

    private fun content(text: String) = ReadResult.Content(text, FileVersion(0, ""))

    private fun TestScope.core(storage: NoteStorage): BlackboardCore =
        BlackboardCore({ storage }, backgroundScope, StandardTestDispatcher(testScheduler), debounceMs = 500)

    /** Runs everything due within 10 s; advanceUntilIdle() would skip backgroundScope work. */
    private fun TestScope.settle() {
        advanceTimeBy(10_000)
        runCurrent()
    }

    private fun TestScope.loaded(storage: NoteStorage): BlackboardCore =
        core(storage).also { it.load(); runCurrent() }

    @Test
    fun `existing file is loaded without writing`() = runTest {
        val storage = FakeStorage(content("# Notes"))
        val core = loaded(storage)
        assertTrue(core.isLoaded)
        assertEquals("# Notes", core.currentText())
        assertEquals(SaveStatus.Saved, core.status.value)
        settle()
        assertEquals(emptyList<String>(), storage.writes)
    }

    @Test
    fun `missing file loads as empty text and is not created`() = runTest {
        val storage = FakeStorage(ReadResult.Missing)
        val core = loaded(storage)
        assertEquals("", core.currentText())
        assertEquals(SaveStatus.Saved, core.status.value)
        settle()
        assertEquals(emptyList<String>(), storage.writes)
    }

    @Test
    fun `load is idempotent`() = runTest {
        val storage = FakeStorage(content("x"))
        val core = core(storage)
        core.load()
        core.load()
        runCurrent()
        core.load()
        runCurrent()
        assertEquals(1, storage.reads)
    }

    @Test
    fun `nothing is written before load completes`() = runTest {
        val storage = FakeStorage(content("existing notes"))
        val core = core(storage)
        core.onTextChanged("")
        core.load()
        core.onTextChanged("typed too early")
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(emptyList<String>(), storage.writes)
        assertEquals("existing notes", core.currentText())
    }

    @Test
    fun `load failure disables saving`() = runTest {
        val storage = FakeStorage(ReadResult.Failure(IOException("locked")))
        val core = loaded(storage)
        assertFalse(core.isLoaded)
        val status = core.status.value
        assertTrue(status is SaveStatus.Failed && status.duringLoad)
        core.onTextChanged("text")
        settle()
        core.flush()
        assertEquals(emptyList<String>(), storage.writes)
    }

    @Test
    fun `storage factory exception is a load failure, not a crash`() = runTest {
        val core = BlackboardCore({ throw IllegalStateException("no path") }, backgroundScope,
            StandardTestDispatcher(testScheduler))
        core.load()
        runCurrent()
        assertTrue(core.status.value is SaveStatus.Failed)
    }

    @Test
    fun `many quick changes produce a single write of the latest text`() = runTest {
        val storage = FakeStorage()
        val core = loaded(storage)
        repeat(10) { i ->
            core.onTextChanged("text $i")
            advanceTimeBy(100)
        }
        assertEquals(emptyList<String>(), storage.writes)
        assertEquals(SaveStatus.Saving, core.status.value)
        advanceTimeBy(401)
        runCurrent()
        assertEquals(listOf("text 9"), storage.writes)
        assertEquals(SaveStatus.Saved, core.status.value)
    }

    @Test
    fun `no write before debounce interval elapses`() = runTest {
        val storage = FakeStorage()
        val core = loaded(storage)
        core.onTextChanged("a")
        advanceTimeBy(499)
        runCurrent()
        assertEquals(emptyList<String>(), storage.writes)
        advanceTimeBy(2)
        runCurrent()
        assertEquals(listOf("a"), storage.writes)
    }

    @Test
    fun `edits during a slow write are saved afterwards and never overwritten by older text`() {
        // Real threads: the blocked write must not block the coroutine scheduler.
        val storage = FakeStorage()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val core = BlackboardCore({ storage }, scope, Dispatchers.IO, debounceMs = 20)
            core.load()
            awaitCondition { core.isLoaded }

            val gate = CompletableDeferred<Unit>()
            storage.gate = gate
            core.onTextChanged("v1")
            awaitCondition { storage.gate == null } // write of v1 started and is blocked

            core.onTextChanged("v2")
            core.onTextChanged("v3")
            Thread.sleep(100) // debounce elapsed; second save waits for the mutex
            assertEquals(emptyList<String>(), storage.writes)

            gate.complete(Unit)
            awaitCondition { core.status.value == SaveStatus.Saved }
            assertEquals(listOf("v1", "v3"), storage.writes)
        } finally {
            scope.cancel()
        }
    }

    private fun awaitCondition(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("Condition not met in time")
            Thread.sleep(5)
        }
    }

    @Test
    fun `write failure keeps text and retry saves it`() = runTest {
        val storage = FakeStorage()
        val core = loaded(storage)
        storage.failWrites = true
        core.onTextChanged("important")
        settle()

        val status = core.status.value
        assertTrue(status is SaveStatus.Failed && !status.duringLoad)
        assertEquals("important", core.currentText())
        assertEquals(emptyList<String>(), storage.writes)

        storage.failWrites = false
        core.retrySave()
        settle()
        assertEquals(listOf("important"), storage.writes)
        assertEquals(SaveStatus.Saved, core.status.value)
    }

    @Test
    fun `consecutive failures are counted and reset by a successful save`() = runTest {
        val storage = FakeStorage()
        val core = loaded(storage)
        storage.failWrites = true
        core.onTextChanged("a")
        settle()
        assertEquals(1, (core.status.value as SaveStatus.Failed).consecutiveFailures)
        core.retrySave()
        settle()
        core.onTextChanged("ab")
        settle()
        assertEquals(3, (core.status.value as SaveStatus.Failed).consecutiveFailures)

        storage.failWrites = false
        core.retrySave()
        settle()
        assertEquals(SaveStatus.Saved, core.status.value)

        storage.failWrites = true
        core.onTextChanged("abc")
        settle()
        assertEquals(1, (core.status.value as SaveStatus.Failed).consecutiveFailures)
    }

    @Test
    fun `retry after load failure reloads`() = runTest {
        var fail = true
        val storage = object : NoteStorage {
            override fun read(): ReadResult = if (fail) ReadResult.Failure(IOException("locked")) else content("ok")
            override fun write(text: String): WriteResult = WriteResult.Success(FileVersion(0, ""))
        }
        val core = loaded(storage)
        assertFalse(core.isLoaded)
        fail = false
        core.retrySave()
        runCurrent()
        assertTrue(core.isLoaded)
        assertEquals("ok", core.currentText())
        assertEquals(SaveStatus.Saved, core.status.value)
    }

    @Test
    fun `flush writes latest text immediately and only once`() = runTest {
        val storage = FakeStorage()
        val core = loaded(storage)
        core.onTextChanged("a")
        core.onTextChanged("ab")
        core.flush()
        assertEquals(listOf("ab"), storage.writes)
        core.flush()
        settle()
        assertEquals(listOf("ab"), storage.writes)
        assertEquals(SaveStatus.Saved, core.status.value)
    }

    @Test
    fun `flushBlocking writes latest text even after the scope is cancelled`() {
        val storage = FakeStorage()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val core = BlackboardCore({ storage }, scope, Dispatchers.IO, debounceMs = 60_000)
        core.load()
        awaitCondition { core.isLoaded }

        core.onTextChanged("draft")
        core.onTextChanged("final")
        scope.cancel()

        assertTrue(core.flushBlocking(5_000))
        assertEquals(listOf("final"), storage.writes)
    }

    @Test
    fun `same-length edit is saved and an equal non-String snapshot is ignored`() = runTest {
        val storage = FakeStorage(content("abc"))
        val core = loaded(storage)
        core.onTextChanged(StringBuilder("abc"))
        settle()
        assertEquals(emptyList<String>(), storage.writes)
        core.onTextChanged(StringBuilder("abd"))
        settle()
        assertEquals(listOf("abd"), storage.writes)
        assertEquals("abd", core.currentText())
    }

    @Test
    fun `identical text does not schedule a save`() = runTest {
        val storage = FakeStorage(content("same"))
        val core = loaded(storage)
        core.onTextChanged("same")
        settle()
        assertEquals(emptyList<String>(), storage.writes)
        assertEquals(SaveStatus.Saved, core.status.value)
    }
}
