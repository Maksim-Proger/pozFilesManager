package com.poz.files

import java.io.File

/**
 * Откуда берём файлы. Слева в меню — список таких источников.
 * root — «адрес» верхнего уровня: путь к папке, smb://сервер/ или "0" у DLNA.
 */
sealed interface Source {
    val key: String
    val title: String
    val root: String

    /** Внутренняя память или USB-диск. */
    data class Local(val dir: File, override val title: String, val removable: Boolean) : Source {
        override val key get() = "local:${dir.path}"
        override val root: String get() = dir.path
    }

    /** Общая папка Windows/NAS. */
    data class Smb(val server: SmbServer) : Source {
        override val key get() = "smb:${server.id}"
        override val title get() = server.title
        override val root get() = "smb://${server.host}/"
    }

    /** Медиасервер DLNA (найден в сети сам). У DLNA корневая «папка» всегда "0". */
    data class Dlna(val server: DlnaServer) : Source {
        override val key get() = "dlna:${server.location}"
        override val title get() = server.name
        override val root get() = "0"
    }
}

/**
 * Один файл или папка в списке.
 * location — то, по чему его открыть: путь, адрес smb://… или у DLNA —
 * ID папки (для папок) либо http-ссылка на сам файл.
 */
data class Entry(
    val name: String,
    val isDir: Boolean,
    val location: String,
    val size: Long = -1,        // -1 — неизвестно
    val modified: Long = 0,     // 0 — неизвестно
    val mime: String? = null,   // DLNA сразу сообщает тип файла
) {
    val kind: Kind get() = if (isDir) Kind.Folder else Kind.of(name, mime)
}

/** Тип файла: от него зависят значок, цвет и чем открывать. */
enum class Kind {
    Folder, Video, Audio, Image, Apk, Other;

    companion object {
        private val video = setOf("mp4", "mkv", "avi", "mov", "m4v", "webm", "ts", "m2ts", "mpg", "mpeg", "wmv", "flv", "3gp")
        private val audio = setOf("mp3", "flac", "m4a", "aac", "ogg", "opus", "wav", "wma", "alac")
        private val image = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")

        fun of(name: String, mime: String? = null): Kind {
            mime?.let {
                when {
                    it.startsWith("video/") -> return Video
                    it.startsWith("audio/") -> return Audio
                    it.startsWith("image/") -> return Image
                }
            }
            return when (name.substringAfterLast('.', "").lowercase()) {
                in video -> Video
                in audio -> Audio
                in image -> Image
                "apk" -> Apk
                else -> Other
            }
        }
    }
}

/** Папки сначала, потом файлы; внутри — по имени без учёта регистра. */
fun List<Entry>.sortedForDisplay(): List<Entry> =
    sortedWith(compareByDescending<Entry> { it.isDir }.thenBy { it.name.lowercase() })
