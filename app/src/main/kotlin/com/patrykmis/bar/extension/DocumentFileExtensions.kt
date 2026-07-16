/*
 * SPDX-FileCopyrightText: 2023-2026 Andrew Gunnerson
 * SPDX-FileCopyrightText: 2026 Patryk Miś <foss@patrykmis.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.patrykmis.bar.extension

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.net.toFile
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.IOException

const val DOCUMENTSUI_AUTHORITY = "com.android.externalstorage.documents"
private const val TAG = "DocumentFileExtensions"

private val DocumentFile.context: Context?
    get() = when (uri.scheme) {
        ContentResolver.SCHEME_CONTENT -> {
            javaClass.getDeclaredField("mContext").apply {
                isAccessible = true
            }.get(this) as Context
        }

        else -> null
    }

private val DocumentFile.isTree: Boolean
    get() = uri.scheme == ContentResolver.SCHEME_CONTENT && DocumentsContract.isTreeUri(uri)

private val DocumentFile.isLocal: Boolean
    get() = uri.scheme == ContentResolver.SCHEME_FILE ||
            (uri.scheme == ContentResolver.SCHEME_CONTENT && uri.authority == DOCUMENTSUI_AUTHORITY)

private fun <R> DocumentFile.withChildrenWithColumns(
    columns: Array<String>,
    block: (Cursor, Sequence<Pair<DocumentFile, Cursor>>) -> R,
): R {
    require(isTree) { "Not a tree URI" }

    // These reflection calls access private fields, but everything is part of the
    // androidx.documentfile:documentfile dependency and we control the version of that.
    val constructor = javaClass.getDeclaredConstructor(
        DocumentFile::class.java,
        Context::class.java,
        Uri::class.java,
    ).apply {
        isAccessible = true
    }

    val cursor = context!!.contentResolver.query(
        DocumentsContract.buildChildDocumentsUriUsingTree(
            uri,
            DocumentsContract.getDocumentId(uri),
        ),
        columns + arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
        null, null, null,
    ) ?: throw IOException("Query returned null cursor: $uri: ${columns.contentToString()}")

    return cursor.use {
        val indexDocumentId =
            cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)

        block(cursor, sequence {
            while (cursor.moveToNext()) {
                val documentId = cursor.getString(indexDocumentId)
                val child: DocumentFile = constructor.newInstance(
                    this@withChildrenWithColumns,
                    context,
                    DocumentsContract.buildDocumentUriUsingTree(uri, documentId),
                )

                yield(child to cursor)
            }
        })
    }
}

/**
 * List files along with their display names, but faster for tree URIs.
 *
 * For non-tree URIs, this is equivalent to calling [DocumentFile.listFiles], followed by
 * [DocumentFile.getName] for each entry. For tree URIs, this only performs a single query to the
 * document provider.
 */
fun DocumentFile.listFilesWithNames(): List<Pair<DocumentFile, String?>> {
    if (!isTree) {
        return listFiles().map { it to it.name }
    }

    return try {
        withChildrenWithColumns(arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)) { c, files ->
            val indexDisplayName =
                c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)

            files.map { it.first to it.second.getString(indexDisplayName) }.toList()
        }
    } catch (e: Exception) {
        Log.w(TAG, "Failed to query tree URI", e)
        emptyList()
    }
}

/**
 * This works around occasional DocumentsProvider binder failures by nudging the provider before
 * making calls such as [DocumentFile.createFile].
 */
fun DocumentFile.workAroundBinderBug() {
    Log.d(TAG, "Working around Android binder bug")
    val deadline = System.nanoTime() + 2_000_000_000L

    while (System.nanoTime() < deadline) {
        if (name != null) {
            Log.d(TAG, "Got binder response")
            return
        }

        Thread.sleep(100)
    }

    Log.w(TAG, "Likely did not get binder response")
}

/**
 * Like [DocumentFile.createFile], but explicitly appends file extensions for certain MIME types
 * that are not supported in older versions of Android. This special handling is only applied for
 * local files.
 */
fun DocumentFile.createFileCompat(mimeType: String, displayName: String): DocumentFile? {
    workAroundBinderBug()

    val finalDisplayName = if (isLocal) {
        buildString {
            append(displayName)

            val mimeTypeMap = MimeTypeMap.getSingleton()
            if (!mimeTypeMap.hasMimeType(mimeType)) {
                val ext = mimeTypeMap.getExtensionFromMimeTypeCompat(mimeType)
                if (ext != null && !displayName.endsWith(".$ext", ignoreCase = true)) {
                    append('.')
                    append(ext)
                }
            }
        }
    } else {
        displayName
    }

    return createFile(mimeType, finalDisplayName)
}

/** Like [DocumentFile.renameTo], but preserves the file extension. */
fun DocumentFile.renameToPreserveExt(displayName: String): Boolean {
    val newName = buildString {
        append(displayName)

        // This intentionally just does simple string operations because MimeTypeMap's
        // getExtensionFromMimeType() and getMimeTypeFromExtension() are not consistent with
        // each other. Eg. audio/mp4 -> m4a -> audio/mpeg -> mp3.

        val ext = name!!.substringAfterLast('.', "")
        if (ext.isNotEmpty() && !displayName.endsWith(".$ext", ignoreCase = true)) {
            append('.')
            append(ext)
        }
    }

    return renameTo(newName)
}

private val DocumentFile.flags: Int
    get() {
        require(uri.scheme == ContentResolver.SCHEME_CONTENT) {
            "Not a DocumentsProvider URI"
        }

        context!!.contentResolver.query(
            uri,
            arrayOf(DocumentsContract.Document.COLUMN_FLAGS),
            null, null, null,
        )?.use {
            if (it.moveToFirst()) {
                return it.getInt(0)
            }
        }

        return 0
    }

class NotEfficientlyMovableException(msg: String, cause: Throwable? = null) :
    IllegalArgumentException(msg, cause)

/**
 * Try to efficiently move the file to the [targetParent] directory.
 *
 * A file can be efficiently moved without copy + delete if the source and destination are the same
 * type and the provider/filesystem supports moving it directly.
 */
fun DocumentFile.moveToDirectory(targetParent: DocumentFile): DocumentFile? {
    if (uri.scheme != targetParent.uri.scheme) {
        throw NotEfficientlyMovableException(
            "Source scheme (${uri.scheme}) != target parent scheme (${targetParent.uri.scheme})"
        )
    }

    return when (uri.scheme) {
        ContentResolver.SCHEME_FILE -> {
            val sourceFile = uri.toFile()
            val targetFile = File(targetParent.uri.toFile(), sourceFile.name)

            when {
                sourceFile.absolutePath == targetFile.absolutePath -> this
                sourceFile.renameTo(targetFile) -> DocumentFile.fromFile(targetFile)
                else -> null
            }
        }

        ContentResolver.SCHEME_CONTENT -> {
            if (uri.authority != targetParent.uri.authority) {
                throw NotEfficientlyMovableException(
                    "Source authority (${uri.authority}) != " +
                            "target parent authority (${targetParent.uri.authority})"
                )
            } else if (flags and DocumentsContract.Document.FLAG_SUPPORTS_MOVE == 0) {
                throw NotEfficientlyMovableException("File does not advertise move flag")
            } else if (parentFile == null) {
                throw NotEfficientlyMovableException("File does not have known parent")
            }

            if (parentFile!!.uri == targetParent.uri) {
                return this
            }

            try {
                DocumentsContract.moveDocument(
                    context!!.contentResolver,
                    uri,
                    parentFile!!.uri,
                    targetParent.uri,
                )?.let { DocumentFile.fromTreeUri(context!!, it) }
            } catch (_: Exception) {
                null
            }
        }

        else -> throw IllegalArgumentException("Unsupported scheme: ${uri.scheme}")
    }
}
