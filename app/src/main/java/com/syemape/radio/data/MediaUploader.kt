package com.syemape.radio.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

/** Metadatos de un archivo elegido por el usuario (galería/documentos). */
data class PickedFile(
    val uri: Uri,
    val name: String,
    val size: Long,
    val mime: String,
)

class MediaUploadTooLargeException : IllegalArgumentException("Archivo muy grande (máx 100 MB)")

/**
 * Sube archivos (imágenes, videos, documentos) elegidos por el usuario a
 * `POST /api/media/upload`. Copia el contenido del `Uri` a un archivo temporal
 * del caché (no carga todo en memoria) para soportar videos grandes.
 */
object MediaUploader {
    const val MAX_UPLOAD_BYTES = 100L * 1024 * 1024
    /** Lee nombre, tamaño y tipo MIME de un `Uri` de contenido. */
    fun query(context: Context, uri: Uri): PickedFile {
        val cr = context.contentResolver
        val mime = cr.getType(uri) ?: "application/octet-stream"
        var name = "archivo"
        var size = -1L
        runCatching {
            cr.query(uri, null, null, null, null)?.use { c ->
                val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val si = c.getColumnIndex(OpenableColumns.SIZE)
                if (c.moveToFirst()) {
                    if (ni >= 0 && !c.isNull(ni)) name = c.getString(ni) ?: name
                    if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                }
            }
        }
        return PickedFile(uri, name, size, mime)
    }

    /** image / video / file según el tipo MIME. */
    fun kindOf(mime: String?): String = when {
        mime == null -> "file"
        mime.startsWith("image/") -> "image"
        mime.startsWith("video/") -> "video"
        else -> "file"
    }

    /** Sube el archivo y devuelve la respuesta del backend (key en /uploads). */
    suspend fun upload(context: Context, picked: PickedFile): MediaUpload {
        val tmp = File.createTempFile("upload", null, context.cacheDir)
        try {
            context.contentResolver.openInputStream(picked.uri)?.use { input ->
                tmp.outputStream().buffered().use { out ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_UPLOAD_BYTES) throw MediaUploadTooLargeException()
                        out.write(buffer, 0, count)
                    }
                }
            } ?: throw IllegalStateException("No se pudo abrir el archivo")
            val body = tmp.asRequestBody(picked.mime.toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", picked.name, body)
            return Backend.api.uploadMedia(part)
        } finally {
            tmp.delete()
        }
    }

    /** Sube una foto al perfil de un usuario desde la galería (Cloudinary del backend). */
    suspend fun uploadProfilePhoto(context: Context, userId: String, picked: PickedFile): AuthUser {
        require(picked.mime.startsWith("image/")) { "Selecciona un archivo de imagen." }
        val tmp = File.createTempFile("profile-photo", null, context.cacheDir)
        try {
            context.contentResolver.openInputStream(picked.uri)?.use { input ->
                tmp.outputStream().buffered().use { out ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > 12L * 1024 * 1024) throw IllegalArgumentException("La foto supera el máximo de 12 MB.")
                        out.write(buffer, 0, count)
                    }
                }
            } ?: throw IllegalStateException("No se pudo abrir la foto")
            val body = tmp.asRequestBody(picked.mime.toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", picked.name, body)
            return Backend.api.uploadUserPhoto(userId, part)
        } finally {
            tmp.delete()
        }
    }
}
