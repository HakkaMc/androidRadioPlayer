package com.example.androidradioplayer

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.AudioManager.OnAudioFocusChangeListener
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.ResultReceiver
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import android.view.KeyEvent
import androidx.lifecycle.Observer
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe


class MediaSessionService() : Service() {
    private val LOG_TAG = "MediaSessionService"

    val HANDLER_LOG_CHANGED = 1

    private val binder = LocalBinder()

    private var audioManager: AudioManager? = null
    private var mediaSession: MediaSessionCompat? = null
    private var mediaSessionCallback: MediaSessionCompat.Callback? = null

    private var onAudioFocusChangeListener: OnAudioFocusChangeListener? = null

    private var isSessionActive: Boolean = false

    private var requestAudioFocusId: Long = -1

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    inner class LocalBinder : Binder() {
        fun getService(): MediaSessionService = this@MediaSessionService
    }

    override fun onCreate() {
        Log.v(LOG_TAG, "onCreate")

        EventBus.getDefault().register(this);

        audioManager = this.getSystemService(AUDIO_SERVICE) as AudioManager

        refreshMediaSession(null)
        requestAudioFocus()

        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.v(LOG_TAG, "onStartCommand")
        return super.onStartCommand(intent, flags, startId)
    }

    @Subscribe
    public fun onEvent(event: MessageEvent) {
        Log.v(LOG_TAG, "onEvent: ${event.getMessageName()}")

        val it = event.message
        val messageName = event.getMessageName()

        if(it != null) {
            when (messageName) {
                "REQUEST_AUDIOFOCUS" -> {
                    requestAudioFocusId = it.getLongExtra("requestAudioFocusId", -1)
                    requestAudioFocus()
                }
            }
        }
    }

    fun refreshMediaSession(context: Context?) {
        Log.v(LOG_TAG, "refreshMediaSession")
        mediaSessionCallback = null

        if (getMediaSession() != null) {
            if (getMediaSession()?.isActive()!!) {
                getMediaSession()?.setActive(false)
            }
            mediaSession = null
        }

        if (context != null) {
            mediaSession = MediaSessionCompat(this, "MyMediaSession")

            val intent = Intent(Intent.ACTION_MEDIA_BUTTON)
            val pendingIntent =
                PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

            prepareMediaSessionCallback()

            mediaSession!!.setMediaButtonReceiver(pendingIntent)

            mediaSession!!.addOnActiveChangeListener(MediaSessionCompat.OnActiveChangeListener() {
                fun onActiveChanged() {
                    Log.v(
                        LOG_TAG,
                        "mediaSession - onActiveChanged | Is active: " + getMediaSession()?.isActive()
                    )
                }
            })
        }
    }

    private fun prepareMediaSessionCallback() {
        if (mediaSession != null) {
            mediaSessionCallback = object : MediaSessionCompat.Callback() {
                override fun onCommand(
                    command: String,
                    args: Bundle,
                    cb: ResultReceiver
                ) {
                    Log.v(LOG_TAG, "MediaSession.Callback - onCommand")
                    super.onCommand(command, args, cb)
                }

                override fun onCustomAction(action: String, extras: Bundle) {
                    Log.v(LOG_TAG, "MediaSession.Callback - onCustomAction")
                    super.onCustomAction(action, extras)
                }

                override fun onPrepare() {
                    Log.v(LOG_TAG, "MediaSession.Callback - onPrepare")
                    super.onPrepare()
                }

                override fun onPause() {
                    Log.v(LOG_TAG, "MediaSession.Callback - onPause")
                    super.onPause()
                }

                override fun onStop() {
                    Log.v(LOG_TAG, "MediaSession.Callback - onStop")
                    super.onStop()
                }

                override fun onPlay() {
                    Log.v(LOG_TAG, "MediaSession.Callback - onPlay")
                    super.onPlay()
                }

                override fun onSkipToNext() {
                    Log.v(LOG_TAG, "MediaSession.Callback - onSkipToNext")
                    super.onSkipToNext()
                }

                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    Log.v(LOG_TAG, "mediaSession - onMediaButtonEvent")

                    val event =
                        mediaButtonIntent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)

                    if (event != null) {
                        val keycode = event.keyCode
                        val action = event.action

                        Log.v(LOG_TAG, "mediaSession - key event number " + event.action);
//
                        if (action == KeyEvent.ACTION_UP) {
                            when (keycode) {
                                KeyEvent.KEYCODE_MEDIA_NEXT -> {
                                    Log.v(
                                        LOG_TAG,
                                        "mediaSession - KEYCODE_MEDIA_NEXT"
                                    )
                                    sendMessage("KEYCODE_MEDIA_NEXT")
                                }

                                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                                    Log.v(
                                        LOG_TAG,
                                        "mediaSession - KEYCODE_MEDIA_PREVIOUS"
                                    )
                                    sendMessage("KEYCODE_MEDIA_PREVIOUS")
                                }

                                KeyEvent.KEYCODE_HEADSETHOOK -> {
                                    Log.v(
                                        LOG_TAG,
                                        "mediaSession - KEYCODE_HEADSETHOOK"
                                    )
                                    sendMessage("KEYCODE_MEDIA_NEXT")
                                }

                                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                                    Log.v(
                                        LOG_TAG,
                                        "mediaSession - KEYCODE_MEDIA_PLAY_PAUSE"
                                    )
                                    sendMessage("KEYCODE_MEDIA_NEXT")
                                }

                                KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                                    Log.v(
                                        LOG_TAG,
                                        "mediaSession - KEYCODE_MEDIA_PAUSE"
                                    )
                                    sendMessage("KEYCODE_MEDIA_PAUSE")
                                }

                                KeyEvent.KEYCODE_MEDIA_PLAY -> {
                                    Log.v(
                                        LOG_TAG,
                                        "mediaSession - KEYCODE_MEDIA_PLAY"
                                    )
                                    sendMessage("KEYCODE_MEDIA_PLAY")
                                }

                                else -> {
                                    Log.v(
                                        LOG_TAG,
                                        "mediaSession - KEYCODE_MEDIA_UNKNOWN $keycode"
                                    )
                                }
                            }
                        }
                    }
                    return super.onMediaButtonEvent(mediaButtonIntent)
                }
            }

            mediaSession?.setCallback(mediaSessionCallback)
        }
    }

    private fun setMediaSessionActive(value: Boolean) {
        if (mediaSession != null) {
            isSessionActive = value
            mediaSession?.setActive(value)
        }
    }

    private fun requestAudioFocus(): Boolean {
        var ret = false

        try {
            if (onAudioFocusChangeListener != null) {
                audioManager!!.abandonAudioFocus(onAudioFocusChangeListener)
            }
        } catch (ex: Exception) {
        }

        onAudioFocusChangeListener = OnAudioFocusChangeListener { i ->
            Log.d(LOG_TAG, "onAudioFocusChange: $i")

            when (i) {
                AudioManager.AUDIOFOCUS_GAIN -> {
                    Log.d(LOG_TAG, "AUDIOFOCUS_GAIN")
                    setMediaSessionActive(true)
                }

                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT -> {
                    Log.d(LOG_TAG, "AUDIOFOCUS_GAIN_TRANSIENT")
                    setMediaSessionActive(true)
                }

                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE -> {
                    Log.d(LOG_TAG, "AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE")
                    setMediaSessionActive(true)
                }

                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK -> {
                    Log.d(LOG_TAG, "AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK")
                    setMediaSessionActive(true)
                }

                AudioManager.AUDIOFOCUS_LOSS -> {
                    Log.d(LOG_TAG, "AUDIOFOCUS_LOSS")
                    setMediaSessionActive(false)
                    sendMessage("AUDIOFOCUS_LOSS")
                }

                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    Log.d(LOG_TAG, "AUDIOFOCUS_LOSS_TRANSIENT")
                }

                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    Log.d(LOG_TAG, "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK")
                }

                AudioManager.AUDIOFOCUS_NONE -> {
                    Log.d(LOG_TAG, "AUDIOFOCUS_NONE")
                }

                else -> {
                    Log.d(LOG_TAG, "AUDIOFOCUS_DEFAULT $i")
                }
            }

            sendMessage("MEDIA_SESSION_CHANGED")
        }

        val result = audioManager!!.requestAudioFocus(
            onAudioFocusChangeListener,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN
        )

        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.d(LOG_TAG, "requestAudioFocus: AUDIOFOCUS_REQUEST_GRANTED")
            ret = true
            setMediaSessionActive(true)

            val intent = Intent("from-media-session-message")
            intent.putExtra("messageName", "AUDIOFOCUS_REQUEST_GRANTED")
            intent.putExtra("requestAudioFocusId", requestAudioFocusId)

            EventBus.getDefault().post(MessageEvent(intent));
        } else if (result == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
            Log.d(LOG_TAG, "requestAudioFocus: AUDIOFOCUS_REQUEST_FAILED")
            sendMessage("AUDIOFOCUS_REQUEST_FAILED")
        } else {
            Log.d(
                LOG_TAG,
                "requestAudioFocus: request audio focus failed: $result"
            )
            sendMessage("AUDIOFOCUS_REQUEST_FAILED")
        }

        return ret
    }

    fun getMediaSession(): MediaSessionCompat? {
        return mediaSession
    }

    fun getIsSessionActive(): Boolean{
        return isSessionActive
    }

    private fun sendMessage(messageName: String) {
        val intent = Intent("from-media-session-message")
        // You can also include some extra data.
//        intent.putExtra("message", "This is my message!");
        intent.putExtra("messageName", messageName)

        EventBus.getDefault().post(MessageEvent(intent));
    }

    override fun onDestroy() {
        Log.d(LOG_TAG, "onDestroy")

        EventBus.getDefault().unregister(this);

        try {
            if (onAudioFocusChangeListener != null) {
                audioManager!!.abandonAudioFocus(onAudioFocusChangeListener)
            }
        } catch (ex: Exception) {
        }
        if (mediaSession != null) {
            setMediaSessionActive(false)
            getMediaSession()?.release()
            mediaSession = null
        }
        super.onDestroy()
    }
}