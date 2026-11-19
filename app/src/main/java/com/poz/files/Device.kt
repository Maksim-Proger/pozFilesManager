package com.poz.files

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/** Сколько всего и сколько занято (в байтах). */
data class Usage(val used: Long, val total: Long) {
    val fraction: Float get() = if (total > 0) used.toFloat() / total else 0f
}

/** Всё про саму приставку: накопители, место на них, оперативная память. */
object Device {

    /** Внутренняя память и подключённые USB-диски. */
    fun storages(context: Context): List<Source.Local> {
        val internal = Source.Local(
            dir = Environment.getExternalStorageDirectory(),
            title = context.getString(R.string.internal_storage),
            removable = false,
        )
        val sm = context.getSystemService(StorageManager::class.java)
        val usb = sm.storageVolumes
            .filter { it.isRemovable && it.state == Environment.MEDIA_MOUNTED }
            .mapNotNull { volume ->
                // Путь к диску: /storage/1234-ABCD. На Android 11+ его даёт сама система
                val dir = if (Build.VERSION.SDK_INT >= 30) volume.directory
                else volume.uuid?.let { File("/storage/$it") }
                dir?.let {
                    Source.Local(it, volume.getDescription(context) ?: context.getString(R.string.usb_storage), true)
                }
            }
        return listOf(internal) + usb
    }

    /** Занятое и общее место. Для внутренней памяти считаем весь раздел данных. */
    fun storageUsage(source: Source.Local): Usage {
        val path = if (source.removable) source.dir else Environment.getDataDirectory()
        return try {
            val stat = StatFs(path.path)
            Usage(used = stat.totalBytes - stat.availableBytes, total = stat.totalBytes)
        } catch (e: Exception) {
            Usage(0, 0)
        }
    }

    /** Файлы и папки в папке на накопителе приставки. Скрытые (с точки) не показываем. */
    suspend fun listLocal(path: String): List<Entry> = withContext(Dispatchers.IO) {
        val files = File(path).listFiles() ?: throw IllegalStateException("нет доступа к папке")
        files.filterNot { it.name.startsWith(".") }
            .map { Entry(it.name, it.isDirectory, it.path, if (it.isFile) it.length() else -1, it.lastModified()) }
            .sortedForDisplay()
    }

    // ================= Оперативная память =================

    fun ramUsage(context: Context): Usage {
        val am = context.getSystemService(ActivityManager::class.java)
        val info = ActivityManager.MemoryInfo().also(am::getMemoryInfo)
        return Usage(used = info.totalMem - info.availMem, total = info.totalMem)
    }

    /**
     * «Освободить ОЗУ»: просим систему закрыть фоновые процессы приложений.
     * Это те, что остались в памяти после выхода (кэш). Открытые сейчас
     * приложения и системные службы Android не трогает — это делает сама система.
     * Возвращает, сколько памяти освободилось.
     */
    suspend fun freeRam(context: Context): Long = withContext(Dispatchers.Default) {
        val am = context.getSystemService(ActivityManager::class.java)
        val before = ramUsage(context).used
        val keep = keepAlive(context)
        context.packageManager.getInstalledApplications(0)
            .filter { app ->
                app.packageName !in keep &&
                    // постоянные системные процессы всё равно не закрываются
                    app.flags and ApplicationInfo.FLAG_PERSISTENT == 0
            }
            .forEach { app -> am.killBackgroundProcesses(app.packageName) }
        delay(1500) // даём системе время закрыть процессы и пересчитать память
        (before - ramUsage(context).used).coerceAtLeast(0)
    }

    /**
     * Кого не трогаем: себя, лаунчер (иначе по Home он будет запускаться заново),
     * клавиатуру (она нужна постоянно) и сервисы Google (они сразу перезапустятся,
     * и память займут снова — только лишняя работа).
     */
    private fun keepAlive(context: Context): Set<String> {
        val ime = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.substringBefore('/')
        val home = context.packageManager
            .resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            ?.activityInfo?.packageName
        return setOfNotNull(
            context.packageName, ime, home,
            "com.simple.tvlauncher",          // PozLauncher
            "com.google.android.gms",
            "com.google.android.apps.tv.launcherx",
        )
    }
}
