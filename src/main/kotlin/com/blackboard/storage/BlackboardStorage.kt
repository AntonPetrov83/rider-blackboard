package com.blackboard.storage

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectDataPath
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

/** Identity of a file version: used to detect modifications made outside Blackboard. */
data class FileVersion(val lastModified: Long, val contentHash: String)

sealed interface ReadResult {
    /** The file exists and was decoded as UTF-8. */
    data class Content(val text: String, val version: FileVersion) : ReadResult

    /** The file does not exist yet (nothing has been saved). */
    data object Missing : ReadResult

    data class Failure(val error: IOException) : ReadResult
}

sealed interface WriteResult {
    data class Success(val version: FileVersion) : WriteResult

    data class Failure(val error: IOException) : WriteResult
}

/** Blocking note persistence. Implementations never throw on I/O errors and must not be called on the EDT. */
interface NoteStorage {
    fun read(): ReadResult

    fun write(text: String): WriteResult
}

/**
 * Reads and writes the Blackboard note file. Plain java.nio, no UI or IDE threading dependencies.
 *
 * All methods perform blocking disk I/O and must not be called on the EDT.
 * Methods are synchronized, so concurrent calls never write the same file simultaneously.
 */
class BlackboardStorage(val file: Path) : NoteStorage {

    /** Version of the content most recently read from or written to disk by this instance. */
    @Volatile
    var lastKnownVersion: FileVersion? = null
        private set

    @Synchronized
    override fun read(): ReadResult {
        val bytes = try {
            Files.readAllBytes(file)
        } catch (_: NoSuchFileException) {
            lastKnownVersion = null
            return ReadResult.Missing
        } catch (e: IOException) {
            LOG.warn("Failed to read $file", e)
            return ReadResult.Failure(e)
        }
        val text = try {
            decodeUtf8(bytes)
        } catch (e: CharacterCodingException) {
            LOG.warn("$file is not valid UTF-8", e)
            return ReadResult.Failure(IOException("File is not valid UTF-8: $file", e))
        }
        val version = FileVersion(lastModifiedOrZero(), hash(bytes))
        lastKnownVersion = version
        return ReadResult.Content(text, version)
    }

    /**
     * Atomically replaces the file content: writes a temp file in the same directory and moves it over the target.
     * Creates the parent directory if needed. Never throws on I/O errors.
     */
    @Synchronized
    override fun write(text: String): WriteResult {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        var temp: Path? = null
        try {
            val dir = file.toAbsolutePath().parent
            Files.createDirectories(dir)
            temp = Files.createTempFile(dir, ".${file.fileName}.", ".tmp")
            FileChannel.open(temp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING).use { channel ->
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
            }
            temp = null
            val version = FileVersion(lastModifiedOrZero(), hash(bytes))
            lastKnownVersion = version
            return WriteResult.Success(version)
        } catch (e: IOException) {
            LOG.warn("Failed to write $file", e)
            return WriteResult.Failure(e)
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp)
                } catch (e: IOException) {
                    LOG.debug("Failed to delete temp file $temp", e)
                }
            }
        }
    }

    /**
     * Current on-disk version, or null if the file is missing or unreadable.
     * Reads the whole file to hash it, so it is as expensive as [read].
     */
    @Synchronized
    fun diskVersion(): FileVersion? = try {
        FileVersion(Files.getLastModifiedTime(file).toMillis(), hash(Files.readAllBytes(file)))
    } catch (_: IOException) {
        null
    }

    /**
     * True if the file on disk differs from [lastKnownVersion]: it was created, deleted or its content changed.
     * A timestamp change with identical content is not considered a modification.
     */
    @Synchronized
    fun isModifiedExternally(): Boolean {
        val known = lastKnownVersion
        val lastModified = try {
            Files.getLastModifiedTime(file).toMillis()
        } catch (_: IOException) {
            return known != null
        }
        if (known == null) return true
        if (known.lastModified == lastModified) return false
        return diskVersion()?.contentHash != known.contentHash
    }

    private fun lastModifiedOrZero(): Long = try {
        Files.getLastModifiedTime(file).toMillis()
    } catch (_: IOException) {
        0L
    }

    companion object {
        private val LOG = logger<BlackboardStorage>()

        const val FILE_NAME = "blackboard.md"
        private const val IDEA_DIR = ".idea"
        private const val DATA_DIR = "blackboard"

        /** Storage for [project]. Touches the filesystem — call off the EDT. */
        fun forProject(project: Project): BlackboardStorage {
            val basePath = project.basePath?.let { runCatching { Path.of(it) }.getOrNull() }
            return BlackboardStorage(resolveFile(basePath) { project.getProjectDataPath(DATA_DIR) })
        }

        /**
         * `<basePath>/.idea/blackboard.md` if that location is usable, otherwise `<fallbackDir>/blackboard.md`.
         * Usable means: base path exists and is a directory, and `.idea` is either a writable directory
         * or absent while the base directory itself is writable (it is created on first write).
         */
        fun resolveFile(basePath: Path?, fallbackDir: () -> Path): Path {
            if (basePath != null && Files.isDirectory(basePath)) {
                val ideaDir = basePath.resolve(IDEA_DIR)
                val usable = if (Files.exists(ideaDir)) {
                    Files.isDirectory(ideaDir) && Files.isWritable(ideaDir)
                } else {
                    Files.isWritable(basePath)
                }
                if (usable) return ideaDir.resolve(FILE_NAME)
                LOG.info("$ideaDir is not writable, using IDE project data storage for Blackboard")
            }
            return fallbackDir().resolve(FILE_NAME)
        }

        private fun decodeUtf8(bytes: ByteArray): String {
            val decoded = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
            return decoded.removePrefix("﻿")
        }

        private fun hash(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
