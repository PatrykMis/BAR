package com.patrykmis.bar.output

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Int64Ref
import android.system.Os
import android.system.OsConstants
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.patrykmis.bar.Preferences
import com.patrykmis.bar.extension.NotEfficientlyMovableException
import com.patrykmis.bar.extension.createFileCompat
import com.patrykmis.bar.extension.getExtensionFromMimeTypeCompat
import com.patrykmis.bar.extension.moveToDirectory
import java.io.IOException

class OutputDirUtils(private val context: Context, private val redactor: Redactor) {
    private val prefs = Preferences(context)

    private fun getErrorFallbackName(name: String) = "ERROR_$name"

    /**
     * Try to move [sourceFile] to the user output directory.
     *
     * @return Whether the user output directory is set and the file was successfully moved
     */
    fun tryMoveToUserDir(sourceFile: DocumentFile): DocumentFile? {
        val userDir = prefs.outputDir?.let {
            // Only returns null on API <21
            DocumentFile.fromTreeUri(context, it)!!
        } ?: return null

        val redactedSource = redactor.redact(sourceFile.uri)

        return try {
            val targetFile = try {
                moveFileToDir(sourceFile, userDir)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to move file; using fallback name", e)
                moveFileToDir(sourceFile, userDir, getErrorFallbackName(sourceFile.name!!))
            }
            val redactedTarget = redactor.redact(targetFile.uri)

            Log.i(TAG, "Successfully moved $redactedSource to $redactedTarget")

            targetFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to move $redactedSource to $userDir", e)
            null
        }
    }

    /**
     * Move [sourceFile] to [targetDir].
     *
     * @return The [DocumentFile] for the newly moved file.
     */
    private fun moveFileToDir(
        sourceFile: DocumentFile,
        targetDir: DocumentFile,
        targetName: String = sourceFile.name!!,
    ): DocumentFile {
        if (targetName == sourceFile.name) {
            try {
                val targetFile = sourceFile.moveToDirectory(targetDir)
                if (targetFile != null) {
                    return targetFile
                }

                Log.w(
                    TAG,
                    "Failed to efficiently move ${redactor.redact(sourceFile.uri)} to " +
                            redactor.redact(targetDir.uri)
                )
            } catch (e: NotEfficientlyMovableException) {
                // Intentionally omit the stack trace for expected fallback cases.
                Log.w(
                    TAG,
                    "${redactor.redact(sourceFile.uri)} cannot be efficiently moved to " +
                            "${redactor.redact(targetDir.uri)}: ${e.message}"
                )
            }
        }

        val targetFile = createFileInDir(targetDir, targetName, sourceFile.type!!)
        copyAndDelete(sourceFile, targetFile)
        return targetFile
    }

    /**
     * Move [sourceFile] to [targetFile] via copy + delete.
     *
     * Both files must have already been created.
     */
    private fun copyAndDelete(sourceFile: DocumentFile, targetFile: DocumentFile) {
        try {
            openFile(sourceFile, read = true).use { sourcePfd ->
                openFile(targetFile, write = true, truncate = true).use { targetPfd ->
                    var remain = Os.lseek(sourcePfd.fileDescriptor, 0, OsConstants.SEEK_END)
                    val offset = Int64Ref(0)

                    while (remain > 0) {
                        val ret = Os.sendfile(
                            targetPfd.fileDescriptor,
                            sourcePfd.fileDescriptor,
                            offset,
                            remain
                        )
                        if (ret == 0L) {
                            throw IOException("Unexpected EOF in sendfile()")
                        }

                        remain -= ret
                    }

                    try {
                        Os.fsync(targetPfd.fileDescriptor)
                    } catch (e: ErrnoException) {
                        if (e.errno != OsConstants.EINVAL) {
                            throw e
                        }
                    }
                }
            }

            sourceFile.delete()
        } catch (e: Exception) {
            targetFile.delete()
            throw e
        }
    }

    /**
     * Create [name] in the default output directory.
     *
     * @param name Should not contain a file extension
     * @param mimeType Determines the file extension
     *
     * @throws IOException if the file could not be created in the default directory
     */
    fun createFileInDefaultDir(name: String, mimeType: String): DocumentFile {
        val defaultDir = DocumentFile.fromFile(prefs.defaultOutputDir)
        return createFileWithFallback(defaultDir, name, mimeType)
    }

    private fun createFileWithFallback(
        dir: DocumentFile,
        name: String,
        mimeType: String,
    ): DocumentFile {
        return try {
            createFileInDir(dir, name, mimeType)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create file; using fallback name", e)
            createFileInDir(dir, getErrorFallbackName(name), mimeType)
        }
    }

    /**
     * Create a new file with name [name] inside [dir].
     *
     * @param name Should not contain a file extension
     * @param mimeType Determines the file extension
     *
     * @throws IOException if file creation fails
     */
    private fun createFileInDir(dir: DocumentFile, name: String, mimeType: String): DocumentFile {
        val redactedDir = redactor.redact(dir.uri)
        Log.d(TAG, "Creating ${redactor.redact(name)} with MIME type $mimeType in $redactedDir")

        val file = dir.createFileCompat(mimeType, name)
            ?: throw IOException("Failed to create file ${redactor.redact(name)} in $redactedDir")

        // Some SAF providers fail to append the file extension, so do it ourselves. This uses a
        // simple length heuristic because MimeTypeMap is not fully reversible.
        val createdName = file.name
        if (createdName != null && createdName.length - name.length < 4) {
            val extension = MimeTypeMap.getSingleton().getExtensionFromMimeTypeCompat(mimeType)
            if (extension != null && !createdName.endsWith(".$extension", ignoreCase = true)) {
                Log.d(TAG, "SAF provider failed to append extension to ${redactor.redact(name)}")
                file.renameTo("$createdName.$extension")
            }
        }

        return file
    }

    /**
     * Open seekable file descriptor to [file].
     *
     * @throws IOException if [file] cannot be opened
     */
    fun openFile(file: DocumentFile, truncate: Boolean): ParcelFileDescriptor =
        openFile(file, read = true, write = true, truncate = truncate)

    /**
     * Open file descriptor to [file]. Writable file descriptors are not guaranteed to be seekable
     * unless they are also opened for reading.
     *
     * @throws IOException if [file] cannot be opened
     */
    fun openFile(
        file: DocumentFile,
        read: Boolean = false,
        write: Boolean = false,
        truncate: Boolean = false,
    ): ParcelFileDescriptor {
        require(read || write) { "At least one of read or write must be true" }

        val mode = buildString {
            if (read) append("r")
            if (write) append("w")
            if (truncate && write) append("t")
        }

        return context.contentResolver.openFileDescriptor(file.uri, mode)
            ?: throw IOException("Failed to open file at ${file.uri} with mode '$mode'")
    }

    companion object {
        private val TAG = OutputDirUtils::class.java.simpleName

        val NULL_REDACTOR = object : Redactor {
            override fun redact(msg: String): String = msg
        }
    }

    interface Redactor {
        fun redact(msg: String): String

        fun redact(uri: Uri): String = Uri.decode(uri.toString())
    }
}
