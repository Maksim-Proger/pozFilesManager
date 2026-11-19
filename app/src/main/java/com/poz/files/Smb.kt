package com.poz.files

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import jcifs.CIFSContext
import jcifs.SmbConstants
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import jcifs.smb.SmbRandomAccessFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Properties
import java.util.UUID
import kotlin.math.min

/** Сохранённый SMB-сервер. Пустой user — вход гостем. */
data class SmbServer(
    val id: String = UUID.randomUUID().toString(),
    val host: String,
    val user: String = "",
    val password: String = "",
    val name: String = "",
) {
    val title: String get() = name.ifBlank { host }
}

/**
 * Работа с общими папками SMB (Windows, NAS, роутеры) через библиотеку jcifs-ng.
 * Серверы с логином и паролем храним в настройках приложения — они видны
 * только этому приложению на этой приставке.
 */
object Smb {

    // ================= Сохранённые серверы =================

    fun servers(context: Context): List<SmbServer> {
        val json = prefs(context).getString("servers", "[]") ?: "[]"
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            SmbServer(o.getString("id"), o.getString("host"), o.optString("user"), o.optString("password"), o.optString("name"))
        }
    }

    fun save(context: Context, server: SmbServer) {
        val list = servers(context).filterNot { it.id == server.id } + server
        write(context, list)
    }

    fun delete(context: Context, id: String) = write(context, servers(context).filterNot { it.id == id })

    fun find(context: Context, id: String): SmbServer? = servers(context).firstOrNull { it.id == id }

    private fun write(context: Context, list: List<SmbServer>) {
        val array = JSONArray()
        list.forEach {
            array.put(
                JSONObject().put("id", it.id).put("host", it.host).put("user", it.user)
                    .put("password", it.password).put("name", it.name)
            )
        }
        prefs(context).edit().putString("servers", array.toString()).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences("smb", Context.MODE_PRIVATE)

    // ================= Соединение =================

    private val base: BaseContext by lazy {
        val props = Properties().apply {
            // Windows 10/11 и современные NAS говорят только на SMB2/SMB3
            setProperty("jcifs.smb.client.minVersion", "SMB202")
            setProperty("jcifs.smb.client.maxVersion", "SMB311")
            // Имя компьютера ищем через DNS и широковещательно по сети
            setProperty("jcifs.resolveOrder", "DNS,BCAST")
            setProperty("jcifs.smb.client.connTimeout", "5000")
            setProperty("jcifs.smb.client.responseTimeout", "15000")
        }
        BaseContext(PropertyConfiguration(props))
    }

    /** «Контекст» с логином: через него идут все запросы к серверу. */
    fun context(server: SmbServer): CIFSContext =
        if (server.user.isBlank()) base.withGuestCrendentials()
        else base.withCredentials(NtlmPasswordAuthenticator(null, server.user, server.password))

    /**
     * Содержимое папки. На верхнем уровне (smb://сервер/) — список общих папок.
     * Служебные общие ресурсы с $ на конце (C$, IPC$) прячем.
     */
    suspend fun list(server: SmbServer, url: String): List<Entry> = withContext(Dispatchers.IO) {
        val ctx = context(server)
        SmbFile(url, ctx).use { dir ->
            dir.listFiles().mapNotNull { file ->
                file.use {
                    // jcifs добавляет "/" к именам папок и общих ресурсов
                    val isDir = it.name.endsWith("/")
                    val name = it.name.trimEnd('/')
                    if (name.endsWith("$") || name.startsWith(".")) return@use null
                    if (it.type == SmbConstants.TYPE_PRINTER) return@use null
                    Entry(
                        name = name,
                        isDir = isDir,
                        location = it.url.toString(),
                        size = if (isDir) -1 else it.length(),
                        modified = if (isDir) 0 else it.lastModified(),
                    )
                }
            }.sortedForDisplay()
        }
    }

    /** Файл целиком в память (для картинок; ограничение — чтобы не съесть ОЗУ). */
    suspend fun readBytes(server: SmbServer, url: String, limit: Int): ByteArray = withContext(Dispatchers.IO) {
        SmbFile(url, context(server)).use { file ->
            require(file.length() <= limit) { "файл слишком большой" }
            file.inputStream.use { it.readBytes() }
        }
    }
}

/**
 * Источник данных для плеера Media3: читает видео прямо с SMB-сервера,
 * кусками и с перемоткой (поэтому фильм не нужно сначала скачивать).
 */
@OptIn(UnstableApi::class)
class SmbDataSource(private val server: SmbServer) : BaseDataSource(true) {

    private var file: SmbRandomAccessFile? = null
    private var uri: Uri? = null
    private var bytesLeft = 0L
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        val smb = SmbFile(dataSpec.uri.toString(), Smb.context(server))
        val raf = smb.openRandomAccess("r")
        raf.seek(dataSpec.position)
        bytesLeft = if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.length
        else smb.length() - dataSpec.position
        file = raf
        opened = true
        transferStarted(dataSpec)
        return bytesLeft
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesLeft == 0L) return C.RESULT_END_OF_INPUT
        val read = file!!.read(buffer, offset, min(length.toLong(), bytesLeft).toInt())
        if (read < 0) return C.RESULT_END_OF_INPUT
        bytesLeft -= read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        try {
            file?.close()
        } finally {
            file = null
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }

    class Factory(private val server: SmbServer) : DataSource.Factory {
        override fun createDataSource(): DataSource = SmbDataSource(server)
    }
}
