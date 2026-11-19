package com.poz.files

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.format.Formatter
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * PozFiles: файловый менеджер для Android TV.
 * Activity — связь с системой (разрешения, USB, сеть, запуск файлов);
 * рисует всё FilesScreen.
 */
class MainActivity : ComponentActivity(), FilesActions {

    private val browser by lazy { Browser(lifecycleScope) }
    private var sidebar by mutableStateOf(SidebarData())

    // Окна поверх экрана
    private var smbDialog by mutableStateOf<SmbDialogState?>(null)
    private var viewer by mutableStateOf<ViewerState?>(null)

    private class SmbDialogState(val server: SmbServer?)
    private class ViewerState(val source: Source, val images: List<Entry>, val index: Int)

    private val permissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refreshStorages() }

    // Вставили или вытащили флешку — обновляем список накопителей
    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refreshStorages()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PozTheme {
                FilesScreen(browser, sidebar, this@MainActivity)
                smbDialog?.let { state ->
                    SmbDialog(
                        initial = state.server,
                        onSave = { Smb.save(this, it); smbDialog = null; refreshSmb(); selectSource(Source.Smb(it)) },
                        onDelete = { deleteSmb(it) },
                        onDismiss = { smbDialog = null },
                    )
                }
                viewer?.let { v ->
                    ImageViewer(v.source, v.images, v.index, onClose = { viewer = null })
                }
            }
        }

        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissions.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
        }

        val usbFilter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addDataScheme("file")
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(usbReceiver, usbFilter, RECEIVER_NOT_EXPORTED)
        else registerReceiver(usbReceiver, usbFilter)

        refreshStorages()
        refreshSmb()
        refreshDlna()

        // Пока приложение на экране — раз в 5 секунд обновляем цифры памяти
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    sidebar = sidebar.copy(ram = Device.ramUsage(this@MainActivity))
                    delay(5000)
                }
            }
        }
    }

    override fun onDestroy() {
        unregisterReceiver(usbReceiver)
        super.onDestroy()
    }

    // ================= Источники =================

    private fun refreshStorages() {
        lifecycleScope.launch {
            val storages = withContext(Dispatchers.IO) {
                Device.storages(this@MainActivity).map { it to Device.storageUsage(it) }
            }
            sidebar = sidebar.copy(storages = storages)
            // Открытую флешку вытащили — закрываем её
            val current = browser.source
            if (current is Source.Local && storages.none { it.first.key == current.key }) browser.close()
        }
    }

    private fun refreshSmb() {
        sidebar = sidebar.copy(smb = Smb.servers(this))
    }

    override fun refreshDlna() {
        if (sidebar.dlnaSearching) return
        sidebar = sidebar.copy(dlnaSearching = true)
        lifecycleScope.launch {
            val found = runCatching { Dlna.discover(this@MainActivity) }.getOrDefault(emptyList())
            sidebar = sidebar.copy(dlna = found, dlnaSearching = false)
        }
    }

    override fun selectSource(source: Source) = browser.open(source)

    override fun addSmb() {
        smbDialog = SmbDialogState(null)
    }

    override fun editSmb(server: SmbServer) {
        smbDialog = SmbDialogState(server)
    }

    private fun deleteSmb(server: SmbServer) {
        Smb.delete(this, server.id)
        smbDialog = null
        if ((browser.source as? Source.Smb)?.server?.id == server.id) browser.close()
        refreshSmb()
    }

    // ================= Оперативная память =================

    override fun freeRam() {
        if (sidebar.freeing) return
        sidebar = sidebar.copy(freeing = true, ramMessage = null)
        lifecycleScope.launch {
            val freed = Device.freeRam(this@MainActivity)
            val message = if (freed > 0) getString(R.string.freed_ram, Formatter.formatShortFileSize(this@MainActivity, freed))
            else getString(R.string.freed_nothing)
            sidebar = sidebar.copy(freeing = false, ramMessage = message, ram = Device.ramUsage(this@MainActivity))
            delay(5000)
            sidebar = sidebar.copy(ramMessage = null)
        }
    }

    // ================= Открытие файлов =================

    override fun open(entry: Entry) {
        val source = browser.source ?: return
        when (entry.kind) {
            Kind.Folder -> browser.enter(entry)
            Kind.Video, Kind.Audio -> {
                val audio = entry.kind == Kind.Audio
                when (source) {
                    is Source.Local -> PlayerActivity.start(this, Uri.fromFile(File(entry.location)).toString(), entry.name, audio)
                    is Source.Smb -> PlayerActivity.start(this, entry.location, entry.name, audio, source.server.id)
                    is Source.Dlna -> PlayerActivity.start(this, entry.location, entry.name, audio)
                }
            }
            Kind.Image -> {
                val images = (browser.listing as? Listing.Ready)?.entries?.filter { it.kind == Kind.Image }.orEmpty()
                viewer = ViewerState(source, images, images.indexOf(entry).coerceAtLeast(0))
            }
            Kind.Apk, Kind.Other -> if (source is Source.Local) openExternally(File(entry.location))
            else toast(getString(R.string.remote_unsupported))
        }
    }

    /** Документы, APK и прочее — отдаём системе: пусть откроет подходящее приложение. */
    private fun openExternally(file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())
            ?: if (file.extension.equals("apk", true)) "application/vnd.android.package-archive" else "*/*"
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            toast(getString(R.string.cant_open_file))
        }
    }

    override fun exit() = finish()

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}
