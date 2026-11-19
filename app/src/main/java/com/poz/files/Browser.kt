package com.poz.files

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Что сейчас показывает правая часть экрана. */
sealed interface Listing {
    data object Idle : Listing
    data object Loading : Listing
    data class Ready(val entries: List<Entry>) : Listing
    data class Error(val message: String) : Listing
}

/** Одна ступенька пути: «Внутренняя память › Movies › 2024». */
data class Crumb(val title: String, val location: String)

/**
 * Состояние просмотра файлов: какой источник открыт, где мы в нём
 * и что загружено. Экран только читает эти поля и вызывает методы.
 */
class Browser(private val scope: CoroutineScope) {

    var source by mutableStateOf<Source?>(null)
        private set
    val path = mutableStateListOf<Crumb>()
    var listing by mutableStateOf<Listing>(Listing.Idle)
        private set

    /** После перехода вверх ставим фокус на папку, из которой вышли. */
    var focusLocation by mutableStateOf<String?>(null)

    private var job: Job? = null

    fun open(source: Source) {
        this.source = source
        path.clear()
        path += Crumb(source.title, source.root)
        focusLocation = null
        load()
    }

    fun enter(entry: Entry) {
        path += Crumb(entry.name, entry.location)
        focusLocation = null
        load()
    }

    /** На уровень вверх. false — уже в корне. */
    fun up(): Boolean {
        if (path.size <= 1) return false
        focusLocation = path.removeAt(path.lastIndex).location
        load()
        return true
    }

    fun reload() = load()

    /** Источник пропал (вытащили флешку, удалили сервер) — закрываем его. */
    fun close() {
        job?.cancel()
        source = null
        path.clear()
        listing = Listing.Idle
    }

    private fun load() {
        val src = source ?: return
        val location = path.last().location
        job?.cancel()
        listing = Listing.Loading
        job = scope.launch {
            listing = try {
                Listing.Ready(list(src, location))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Listing.Error(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private suspend fun list(source: Source, location: String): List<Entry> = when (source) {
        is Source.Local -> Device.listLocal(location)
        is Source.Smb -> Smb.list(source.server, location)
        is Source.Dlna -> Dlna.browse(source.server, location)
    }
}
