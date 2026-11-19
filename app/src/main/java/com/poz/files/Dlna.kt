package com.poz.files

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URL

/** Медиасервер DLNA: имя и адрес, по которому у него запрашивают папки. */
data class DlnaServer(val name: String, val location: String, val controlUrl: String)

/**
 * DLNA своими руками, без библиотек. Два шага:
 * 1) Поиск (SSDP): кричим в сеть «кто тут медиасервер?» и собираем ответы.
 * 2) Просмотр (UPnP ContentDirectory): просим у сервера содержимое папки
 *    запросом Browse и разбираем ответ — список папок и файлов (DIDL-Lite).
 */
object Dlna {

    private const val SSDP_ADDRESS = "239.255.255.250"
    private const val SSDP_PORT = 1900
    private const val MEDIA_SERVER = "urn:schemas-upnp-org:device:MediaServer:1"
    private const val CONTENT_DIRECTORY = "urn:schemas-upnp-org:service:ContentDirectory:1"

    /** Ищем серверы ~3 секунды. */
    suspend fun discover(context: Context): List<DlnaServer> = withContext(Dispatchers.IO) {
        // Без этой «блокировки» Wi-Fi на приставках часто выбрасывает широковещательные пакеты
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        val lock = wifi.createMulticastLock("pozfiles-dlna").apply { setReferenceCounted(false); acquire() }
        val locations = linkedSetOf<String>()
        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = 500
                val request = (
                    "M-SEARCH * HTTP/1.1\r\n" +
                        "HOST: $SSDP_ADDRESS:$SSDP_PORT\r\n" +
                        "MAN: \"ssdp:discover\"\r\n" +
                        "MX: 2\r\n" +
                        "ST: $MEDIA_SERVER\r\n\r\n"
                    ).toByteArray()
                val group = InetAddress.getByName(SSDP_ADDRESS)
                // UDP может потеряться — отправляем дважды
                repeat(2) { socket.send(DatagramPacket(request, request.size, group, SSDP_PORT)) }

                val buffer = ByteArray(4096)
                val until = System.currentTimeMillis() + 3000
                while (System.currentTimeMillis() < until) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        val text = String(packet.data, 0, packet.length)
                        LOCATION.find(text)?.groupValues?.get(1)?.let(locations::add)
                    } catch (e: SocketTimeoutException) {
                        // тишина — ждём дальше до конца отведённого времени
                    }
                }
            }
        } finally {
            lock.release()
        }
        locations.mapNotNull { runCatching { describe(it) }.getOrNull() }.distinctBy { it.controlUrl }
    }

    private val LOCATION = Regex("(?im)^location:\\s*(\\S+)")

    /** Читаем «паспорт» устройства: имя и адрес службы каталога. */
    private fun describe(location: String): DlnaServer? {
        val xml = httpGet(location)
        val parser = Xml.newPullParser().apply { setInput(StringReader(xml)) }
        var name: String? = null
        var urlBase: String? = null
        var serviceType: String? = null
        var controlUrl: String? = null
        var tag = ""
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> tag = parser.name
                XmlPullParser.TEXT -> when (tag) {
                    "friendlyName" -> if (name == null) name = parser.text.trim()
                    "URLBase" -> urlBase = parser.text.trim()
                    "serviceType" -> serviceType = parser.text.trim()
                    "controlURL" -> if (serviceType?.contains("ContentDirectory") == true && controlUrl == null) {
                        controlUrl = parser.text.trim()
                    }
                }
                XmlPullParser.END_TAG -> tag = ""
            }
        }
        val control = controlUrl ?: return null
        val absolute = URL(URL(urlBase ?: location), control).toString()
        return DlnaServer(name ?: location, location, absolute)
    }

    /** Содержимое папки сервера. Большие папки сервер отдаёт порциями — собираем все. */
    suspend fun browse(server: DlnaServer, objectId: String): List<Entry> = withContext(Dispatchers.IO) {
        val result = mutableListOf<Entry>()
        var start = 0
        while (start < 5000) {
            val response = soapBrowse(server.controlUrl, objectId, start, 200)
            val page = parseDidl(response.didl)
            result += page
            start += response.returned
            if (response.returned == 0 || start >= response.total) break
        }
        result.sortedForDisplay()
    }

    private class BrowseResponse(val didl: String, val returned: Int, val total: Int)

    private fun soapBrowse(controlUrl: String, objectId: String, start: Int, count: Int): BrowseResponse {
        val body = """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
<s:Body><u:Browse xmlns:u="$CONTENT_DIRECTORY">
<ObjectID>${xmlEscape(objectId)}</ObjectID>
<BrowseFlag>BrowseDirectChildren</BrowseFlag>
<Filter>*</Filter>
<StartingIndex>$start</StartingIndex>
<RequestedCount>$count</RequestedCount>
<SortCriteria></SortCriteria>
</u:Browse></s:Body></s:Envelope>"""
        val connection = URL(controlUrl).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 5000
        connection.readTimeout = 15000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
        connection.setRequestProperty("SOAPACTION", "\"$CONTENT_DIRECTORY#Browse\"")
        try {
            connection.outputStream.use { it.write(body.toByteArray()) }
            val xml = connection.inputStream.bufferedReader().use { it.readText() }
            // Ответ — конверт SOAP; внутри поля Result лежит экранированный DIDL-Lite
            val parser = Xml.newPullParser().apply { setInput(StringReader(xml)) }
            var didl = ""
            var returned = 0
            var total = 0
            var tag = ""
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> tag = parser.name.substringAfter(':')
                    XmlPullParser.TEXT -> when (tag) {
                        "Result" -> didl += parser.text
                        "NumberReturned" -> returned = parser.text.trim().toIntOrNull() ?: 0
                        "TotalMatches" -> total = parser.text.trim().toIntOrNull() ?: 0
                    }
                    XmlPullParser.END_TAG -> tag = ""
                }
            }
            return BrowseResponse(didl, returned, total)
        } finally {
            connection.disconnect()
        }
    }

    /** DIDL-Lite: <container> — папки, <item> — файлы со ссылкой в <res>. */
    private fun parseDidl(didl: String): List<Entry> {
        if (didl.isBlank()) return emptyList()
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(StringReader(didl))
        }
        val entries = mutableListOf<Entry>()
        var isContainer = false
        var id = ""
        var title = ""
        var url: String? = null
        var mime: String? = null
        var size = -1L
        var tag = ""
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    tag = parser.name
                    when (tag) {
                        "container", "item" -> {
                            isContainer = tag == "container"
                            id = parser.getAttributeValue(null, "id") ?: ""
                            title = ""; url = null; mime = null; size = -1
                        }
                        // У файла бывает несколько <res> (разные качества) — берём первый
                        "res" -> if (url == null) {
                            mime = parser.getAttributeValue(null, "protocolInfo")?.split(':')?.getOrNull(2)
                            size = parser.getAttributeValue(null, "size")?.toLongOrNull() ?: -1
                        }
                    }
                }
                XmlPullParser.TEXT -> when (tag) {
                    "dc:title" -> title += parser.text
                    "res" -> if (url == null) url = parser.text.trim().ifEmpty { null }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "container" -> entries += Entry(title.trim(), isDir = true, location = id)
                        "item" -> url?.let { entries += Entry(title.trim(), false, it, size, mime = mime) }
                    }
                    tag = ""
                }
            }
        }
        return entries
    }

    private fun httpGet(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 4000
        connection.readTimeout = 4000
        return try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun xmlEscape(text: String) =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
