package com.blackboard.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import kotlin.io.path.listDirectoryEntries

class BlackboardStorageTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun root(): Path = tmp.root.toPath()

    private fun storage(): BlackboardStorage =
        BlackboardStorage(root().resolve(".idea").resolve(BlackboardStorage.FILE_NAME))

    private fun ReadResult.text(): String = when (this) {
        is ReadResult.Content -> text
        else -> throw AssertionError("Expected content, got $this")
    }

    @Test
    fun `missing file reads as Missing and is not created`() {
        val storage = storage()
        assertEquals(ReadResult.Missing, storage.read())
        assertFalse(Files.exists(storage.file))
        assertFalse(Files.exists(storage.file.parent))
    }

    @Test
    fun `write creates parent directory and file`() {
        val storage = storage()
        assertTrue(storage.write("# Notes") is WriteResult.Success)
        assertTrue(Files.isRegularFile(storage.file))
        assertEquals("# Notes", storage.read().text())
    }

    @Test
    fun `utf8 content and markdown survive roundtrip unchanged`() {
        val text = "# Заметки 📝\n\n- пункт\r\n```csharp\nawait service.Run();\n```\n\t👩‍💻 ✓ 日本語\n"
        val storage = storage()
        storage.write(text)
        assertEquals(text, storage.read().text())
        assertEquals(text, String(Files.readAllBytes(storage.file), StandardCharsets.UTF_8))
    }

    @Test
    fun `existing file is read`() {
        val storage = storage()
        Files.createDirectories(storage.file.parent)
        Files.writeString(storage.file, "existing\n")
        assertEquals("existing\n", storage.read().text())
    }

    @Test
    fun `utf8 bom is stripped on read`() {
        val storage = storage()
        Files.createDirectories(storage.file.parent)
        Files.write(storage.file, byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "hi".toByteArray())
        assertEquals("hi", storage.read().text())
    }

    @Test
    fun `invalid utf8 is a read failure, not garbled text`() {
        val storage = storage()
        Files.createDirectories(storage.file.parent)
        Files.write(storage.file, byteArrayOf(0xC3.toByte(), 0x28))
        assertTrue(storage.read() is ReadResult.Failure)
    }

    @Test
    fun `overwrite replaces content and leaves no temp files`() {
        val storage = storage()
        storage.write("first version that is longer")
        storage.write("second")
        assertEquals("second", storage.read().text())
        assertEquals(listOf(storage.file), storage.file.parent.listDirectoryEntries())
    }

    @Test
    fun `write failure is reported, not thrown`() {
        val notADir = root().resolve("file")
        Files.writeString(notADir, "x")
        val storage = BlackboardStorage(notADir.resolve("sub").resolve(BlackboardStorage.FILE_NAME))
        assertTrue(storage.write("text") is WriteResult.Failure)
        assertTrue(storage.read() is ReadResult.Missing || storage.read() is ReadResult.Failure)
    }

    @Test
    fun `read failure is reported, not thrown`() {
        val storage = BlackboardStorage(root())  // a directory cannot be read as a file
        assertTrue(storage.read() is ReadResult.Failure)
    }

    @Test
    fun `version tracks reads and writes`() {
        val storage = storage()
        assertNull(storage.lastKnownVersion)
        val written = (storage.write("a") as WriteResult.Success).version
        assertEquals(written, storage.lastKnownVersion)
        assertEquals(written, storage.diskVersion())
        assertFalse(storage.isModifiedExternally())
    }

    @Test
    fun `external content change is detected`() {
        val storage = storage()
        storage.write("mine")
        Files.writeString(storage.file, "theirs")
        Files.setLastModifiedTime(storage.file, FileTime.fromMillis(storage.lastKnownVersion!!.lastModified + 5_000))
        assertTrue(storage.isModifiedExternally())
    }

    @Test
    fun `touch without content change is not a modification`() {
        val storage = storage()
        storage.write("same")
        Files.setLastModifiedTime(storage.file, FileTime.fromMillis(storage.lastKnownVersion!!.lastModified + 5_000))
        assertFalse(storage.isModifiedExternally())
    }

    @Test
    fun `external creation and deletion are detected`() {
        val storage = storage()
        assertFalse(storage.isModifiedExternally())
        Files.createDirectories(storage.file.parent)
        Files.writeString(storage.file, "created")
        assertTrue(storage.isModifiedExternally())
        storage.read()
        Files.delete(storage.file)
        assertTrue(storage.isModifiedExternally())
    }

    @Test
    fun `resolve uses idea dir when it is writable`() {
        Files.createDirectories(root().resolve(".idea"))
        val path = BlackboardStorage.resolveFile(root()) { throw AssertionError("fallback must not be used") }
        assertEquals(root().resolve(".idea").resolve(BlackboardStorage.FILE_NAME), path)
    }

    @Test
    fun `resolve uses idea dir when it does not exist yet and does not create it`() {
        val path = BlackboardStorage.resolveFile(root()) { throw AssertionError("fallback must not be used") }
        assertEquals(root().resolve(".idea").resolve(BlackboardStorage.FILE_NAME), path)
        assertFalse(Files.exists(root().resolve(".idea")))
    }

    @Test
    fun `resolve falls back without base path`() {
        val fallback = root().resolve("data")
        assertEquals(fallback.resolve(BlackboardStorage.FILE_NAME), BlackboardStorage.resolveFile(null) { fallback })
    }

    @Test
    fun `resolve falls back when base path is missing`() {
        val fallback = root().resolve("data")
        val path = BlackboardStorage.resolveFile(root().resolve("missing")) { fallback }
        assertEquals(fallback.resolve(BlackboardStorage.FILE_NAME), path)
    }

    @Test
    fun `resolve falls back when idea is not a directory`() {
        Files.writeString(root().resolve(".idea"), "not a dir")
        val fallback = root().resolve("data")
        assertEquals(fallback.resolve(BlackboardStorage.FILE_NAME), BlackboardStorage.resolveFile(root()) { fallback })
    }

    @Test
    fun `different projects resolve to different files`() {
        val a = root().resolve("a").also(Files::createDirectories)
        val b = root().resolve("b").also(Files::createDirectories)
        val fallback = { throw AssertionError("fallback must not be used") }
        assertFalse(BlackboardStorage.resolveFile(a, fallback) == BlackboardStorage.resolveFile(b, fallback))
    }
}
