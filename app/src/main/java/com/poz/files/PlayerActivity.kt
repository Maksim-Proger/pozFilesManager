package com.poz.files

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView

/**
 * Встроенный плеер видео и музыки на Google Media3.
 * Пульт: OK — пауза и панель управления, ←/→ — перемотка, Назад — выход.
 */
@OptIn(UnstableApi::class)
class PlayerActivity : Activity() {

    private var player: ExoPlayer? = null
    private lateinit var view: PlayerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Пока смотрим видео, экран не гаснет
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val uri = Uri.parse(intent.getStringExtra(EXTRA_URI))
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val smbServer = intent.getStringExtra(EXTRA_SMB_ID)?.let { Smb.find(this, it) }
        val audio = intent.getBooleanExtra(EXTRA_AUDIO, false)

        // Откуда читать байты: SMB — наш источник, остальное (файл, http) — стандартный
        val dataSource = smbServer?.let { SmbDataSource.Factory(it) } ?: DefaultDataSource.Factory(this)
        val item = MediaItem.Builder()
            .setUri(uri)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
            .build()

        val exo = ExoPlayer.Builder(this)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
        exo.setMediaSource(ProgressiveMediaSource.Factory(dataSource).createMediaSource(item))
        exo.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(this@PlayerActivity, getString(R.string.error_open, error.errorCodeName), Toast.LENGTH_LONG).show()
                finish()
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) finish()
            }
        })
        exo.prepare()
        exo.playWhenReady = true
        player = exo

        view = PlayerView(this).apply {
            this.player = exo
            keepScreenOn = true
            setShowSubtitleButton(true)
            if (audio) {
                // Музыка: вместо чёрного экрана — нота, панель управления не прячется
                defaultArtwork = getDrawable(R.drawable.music_artwork)
                controllerShowTimeoutMs = 0
                controllerHideOnTouch = false
            } else {
                controllerShowTimeoutMs = 3000
            }
        }
        // Сверху — название файла (у видео прячется вместе с панелью)
        val titleView = TextView(this).apply {
            text = title
            textSize = 22f
            setTextColor(Color.WHITE)
            setShadowLayer(8f, 0f, 2f, Color.BLACK)
            setPadding(64, 40, 64, 0)
            typeface = resources.getFont(R.font.inter)
        }
        view.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
            titleView.visibility = visibility
        })
        setContentView(FrameLayout(this).apply {
            addView(view)
            addView(titleView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP))
        })
        if (audio) view.showController()
    }

    // Кнопки пульта отдаём плееру: он сам покажет панель и перемотает
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(event)
        if (!view.isControllerFullyVisible && event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> { player?.seekBack(); view.showController(); return true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { player?.seekForward(); view.showController(); return true }
            }
        }
        return view.dispatchKeyEvent(event) || super.dispatchKeyEvent(event)
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_URI = "uri"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_SMB_ID = "smb"
        private const val EXTRA_AUDIO = "audio"

        fun start(context: Context, uri: String, title: String, audio: Boolean, smbServerId: String? = null) {
            context.startActivity(
                Intent(context, PlayerActivity::class.java)
                    .putExtra(EXTRA_URI, uri)
                    .putExtra(EXTRA_TITLE, title)
                    .putExtra(EXTRA_AUDIO, audio)
                    .putExtra(EXTRA_SMB_ID, smbServerId)
            )
        }
    }
}
