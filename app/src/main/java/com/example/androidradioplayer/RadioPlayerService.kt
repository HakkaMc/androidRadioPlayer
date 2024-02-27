package com.example.androidradioplayer

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Binder
import android.os.Handler
import android.util.Log
import androidx.lifecycle.Observer
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Scanner
import java.util.Stack


class RadioPlayerService() : Service() {
    private val LOG_TAG = "RadioPlayerService"

    val RADIO_STATUS_CHANGED = "RADIO_STATUS_CHANGED"

    val RADIO_SOURCE_CHANGED = "RADIO_SOURCE_CHANGED"

    private val radios = ArrayList<Radio>()

    private val decodedRadioUrlThreads = Stack<Thread>()

    private var audioManager: AudioManager? = null

    private lateinit var exoPlayer: ExoPlayer

    private var requestAudioFocusId: Long = -2 // -1 granted; -2 not granted

    private var playingRadio: Radio? = null

    private var playerStatus = ""

    private var playingUrlIndex = 0

    private var isInPlayingProcess = false

//    private var playerStatusList = java.util.ArrayList<String>()

    private var decodedRadioUrl = ""

    private var decodeRadioUrlThreadId = -1

    private var playThread: Thread? = null

    private var prepareStartTime: Long = 0

    public var JSONdownloadResponseTime: Long = 0
    override fun onBind(intent: Intent) = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService() = this@RadioPlayerService
    }

    init {
        var radio = Radio("Kiss 98", "kiss98", "play")
        radio.addUrl("https://api.play.cz/json/getStream/kiss/mp3/128", "128")
        radio.addUrl("https://api.play.cz/json/getStream/kiss/mp3/64", "64")
        radios.add(radio)

        radio = Radio("Beat", "beat", "play")
        radio.addUrl("https://api.play.cz/json/getStream/beat/mp3/128", "128")
        radio.addUrl("https://api.play.cz/json/getStream/beat/mp3/64", "64")
        radios.add(radio)

        radio = Radio("Hey", "hey", "play")
        radio.addUrl("https://api.play.cz/json/getStream/hey-radio/mp3/128", "128")
        radio.addUrl("https://api.play.cz/json/getStream/hey-radio/mp3/64", "64")
        radios.add(radio)

        radio = Radio("Expres FM", "expresfm", "play")
        radio.addUrl("https://api.play.cz/json/getStream/expres/mp3/128", "128")
        radio.addUrl("https://api.play.cz/json/getStream/expres/mp3/64", "64")
        radios.add(radio)

        radio = Radio("Rock Zone", "rockzone", "play")
        radio.addUrl("https://api.play.cz/json/getStream/rockzone/mp3/128", "128")
        radio.addUrl("https://api.play.cz/json/getStream/rockzone/mp3/64", "64")
        radios.add(radio)

        radio = Radio("Rádio Čas Rock", "casrock", "play")
        radio.addUrl("https://api.play.cz/json/getStream/casrock/mp3/128", "128")
        radio.addUrl("https://api.play.cz/json/getStream/casrock/mp3/64", "64")
        radios.add(radio)

        radio = Radio("RFI", "rfi", "infomaniak")
        radio.addUrl("https://live-reflector.ice.infomaniak.ch/rfimonde-64.mp3", "64")
        radios.add(radio)
    }

    override fun onCreate() {
        super.onCreate()

        EventBus.getDefault().register(this);

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager?

        exoPlayer = ExoPlayer.Builder(this).build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val applicationId = applicationContext.applicationInfo.packageName
        return super.onStartCommand(intent, flags, startId)
    }
    @Subscribe
    public fun onEvent(event: MessageEvent) {
        Log.v(LOG_TAG, "onEvent: ${event.getMessageName()}")

        val it = event.message
        val messageName = event.getMessageName()

        if (it != null) {
            when (messageName) {
                "AUDIOFOCUS_REQUEST_GRANTED" -> {
                    val receivedRequestAudioFocusId =
                        it.getLongExtra("requestAudioFocusId", -1)

                    Log.v(
                        "AUDIOFOCUS_REQUEST_GRANTED",
                        "${receivedRequestAudioFocusId} vs ${requestAudioFocusId}"
                    )

//                requestAudioFocusId2 = receivedRequestAudioFocusId

                    if (receivedRequestAudioFocusId == requestAudioFocusId) {
                        requestAudioFocusId = -1
                        Log.v(LOG_TAG, "AUDIOFOCUS_REQUEST_GRANTED")
                        updatePlayerStatus("AUDIOFOCUS_REQUEST_GRANTED ")
                    } else {
                        requestAudioFocusId = -2
                        Log.v(LOG_TAG, "AUDIOFOCUS_REQUEST_FAILED")
                        updatePlayerStatus("AUDIOFOCUS_REQUEST_FAILED ")
                    }
                }

                "AUDIOFOCUS_REQUEST_FAILED" -> {
                    requestAudioFocusId = -2
                    Log.v(LOG_TAG, "AUDIOFOCUS_REQUEST_FAILED")
                    updatePlayerStatus("AUDIOFOCUS_REQUEST_FAILED ")
                }

                "AUDIOFOCUS_LOSS" -> {
                    requestAudioFocusId = -2
                    Log.v(LOG_TAG, "AUDIOFOCUS_LOSS")
                    updatePlayerStatus("AUDIOFOCUS_LOSS ")
                    stopAndSaveState()
                }

                "KEYCODE_MEDIA_NEXT" -> {
                    Log.v(LOG_TAG, "KEYCODE_MEDIA_NEXT")
                    updatePlayerStatus("KEYCODE_MEDIA_NEXT ")
                    if (isInPlayingProcess) {
                        nextRadio()
                    }
                }

                "KEYCODE_MEDIA_PREVIOUS" -> {
                    Log.v(LOG_TAG, "KEYCODE_MEDIA_PREVIOUS")
                    updatePlayerStatus("KEYCODE_MEDIA_PREVIOUS ")
                    if (isInPlayingProcess) {
                        previousRadio()
                    }
                }

                "KEYCODE_MEDIA_PAUSE" -> {
                    Log.v(LOG_TAG, "KEYCODE_MEDIA_PAUSE")
                    updatePlayerStatus("KEYCODE_MEDIA_PAUSE ")

                    if (isInPlayingProcess) {
                        stop()
                    }
                }

                "KEYCODE_MEDIA_PLAY" -> {
                    Log.v(LOG_TAG, "KEYCODE_MEDIA_PLAY")
                    updatePlayerStatus("KEYCODE_MEDIA_PLAY ")

                    if (!isInPlayingProcess && playingRadio != null) {
                        play(playingRadio!!, playingUrlIndex)
                    }
                }
            }
        }
    }

    private fun updatePlayerStatus(msg: String) {
        playerStatus = msg
//        playerStatusList.add(0, msg)
//        if (playerStatusList.size > 50) {
//            val indexFrom = 0
//            val indexTo = Math.min(playerStatusList.size, 50)
//            playerStatusList =
//                java.util.ArrayList<String>(playerStatusList.subList(indexFrom, indexTo))
//        }
        notifyHandlers(RADIO_STATUS_CHANGED, msg)
    }

    fun requestAudioFocus(): Boolean {
        Log.d(LOG_TAG, "requestAudioFocus")
        var ret = false

        requestAudioFocusId = System.currentTimeMillis()
        val originalRequestAudioFocusId = requestAudioFocusId

        val intent = Intent("to-media-session-message")
        intent.putExtra("messageName", "REQUEST_AUDIOFOCUS")
        intent.putExtra("requestAudioFocusId", requestAudioFocusId)

        EventBus.getDefault().post(MessageEvent(intent));

        val thread: Thread = object : Thread() {
            override fun run() {
                var i = 0
                while (requestAudioFocusId == originalRequestAudioFocusId && i < 50) {
                    ++i
                    try {
                        sleep(100)
                    } catch (ex: java.lang.Exception) {
                    }
                }
            }
        }
        thread.start()

        try {
            thread.join()
        } catch (ex: java.lang.Exception) {
            Log.e(LOG_TAG, "requestAudioFocus - thread.join exception: ", ex)
        }

        Log.d(
            LOG_TAG,
            "requestAudioFocus requestAudioFocusId: $requestAudioFocusId"
        )

        if (requestAudioFocusId == -1L) {
            ret = true
        }

        requestAudioFocusId = -2

        return ret
    }

    private fun nextRadio() {
        if (isPlaying() && playingRadio != null) {
            var index = -1
            for (i in radios.indices) {
                val radio = radios[i]
                if (getPlayingRadio() != null && radio.getId().equals(getPlayingRadio()?.getId())) {
                    index = i
                }
            }
            if (index > -1) {
                index = (index + 1) % radios.size
                play(radios[index], 0)
            }
        } else {
            play(radios[0], 0)
        }
    }

    private fun previousRadio() {
        if (isPlaying() && playingRadio != null) {
            var index = -1
            for (i in radios.indices) {
                val radio = radios[i]
                if (getPlayingRadio() != null && radio.getId().equals(getPlayingRadio()?.getId())) {
                    index = i
                }
            }
            if (index > -1) {
                index = (index - 1 + radios.size) % radios.size
                play(radios[index], 0)
            }
        } else {
            play(radios[0], 0)
        }
    }

    fun isPlaying(): Boolean {
        return isInPlayingProcess
    }

    fun play(radio: Radio, urlToPlayIndex: Int) {
        Log.v(LOG_TAG, "play")
        updatePlayerStatus("playing_requested")
        stop()

        if (exoPlayer != null) {
            isInPlayingProcess = true

            playThread = object : Thread() {
                override fun run() {

                    if (requestAudioFocus()) {
                        playingRadio = radio
                        playingUrlIndex = urlToPlayIndex

                        notifyHandlers(RADIO_SOURCE_CHANGED, null)

                        Log.v(LOG_TAG, "play - preparing player")

                        try {
                            if (radio.getType() === "infomaniak") {
                                decodedRadioUrl = radio.getUrls()[0].url
                            }else {
                                if (radio.getDecodedUrls().getOrNull(playingUrlIndex)?.isNullOrEmpty() == false) {
                                    decodedRadioUrl = radio.getDecodedUrls().get(playingUrlIndex)
                                } else {
                                    if(playingUrlIndex > (radio.getUrls().size-1)){
                                        playingUrlIndex = 0
                                    }

                                    if (radio.getUrls().getOrNull(playingUrlIndex) != null) {
                                        updatePlayerStatus("downloading")
                                        getRadioStreamUrl(radio.getUrls()[playingUrlIndex].url)?.join()
                                        radio.setDecodedUrl(decodedRadioUrl, playingUrlIndex)
                                    }
                                }
                            }
                        } catch (ex: Exception) {
                            Log.e(LOG_TAG, "play", ex)
                        }

                        if (decodedRadioUrl.length > 0) {
                            Log.d(
                                LOG_TAG,
                                "play - radioStreamUrl: $decodedRadioUrl"
                            )

                            try {
                                prepareStartTime = System.currentTimeMillis()

                                val handler = Handler(exoPlayer.applicationLooper)

                                handler.post(Runnable {
                                    val mediaItem = MediaItem.fromUri(decodedRadioUrl)
                                    exoPlayer.setMediaItem(mediaItem)

                                    updatePlayerStatus("preparing")

                                    exoPlayer.prepare()
                                    var preparedTime =
                                        System.currentTimeMillis() - prepareStartTime

                                    updatePlayerStatus("prepared")
                                    Log.v(
                                        LOG_TAG,
                                        "Player prepared after ${preparedTime}ms"
                                    )
                                    notifyHandlers("PLAYER_PREPARED", preparedTime.toString())

                                    exoPlayer.play()
                                    updatePlayerStatus("playing")
                                    isInPlayingProcess = true
                                    notifyHandlers("RADIO_STATUS_CHANGED", "")
                                })
                            } catch (ex: Exception) {
                                Log.e(LOG_TAG, "play", ex)
                                updatePlayerStatus("error")
                                isInPlayingProcess = false
                            }
                        } else {
                            updatePlayerStatus("error")
                            isInPlayingProcess = false
                        }
                    } else {
                        isInPlayingProcess = false
                        playingRadio = null
                        playingUrlIndex = -1

                        Log.d(LOG_TAG, "Focus request not granted")
                        updatePlayerStatus("missing permission")

                        notifyHandlers(RADIO_SOURCE_CHANGED, null)
                    }
                }
            }
            (playThread as Thread).start()
        } else {
            updatePlayerStatus("player_is_NULL")
        }
    }

    fun stop() {
        var tmpThread: Thread?
        if (decodedRadioUrlThreads.size > 0) {
            tmpThread = decodedRadioUrlThreads.pop()
            while (tmpThread != null) {
                tmpThread.interrupt()
                tmpThread = if (decodedRadioUrlThreads.size > 0) {
                    decodedRadioUrlThreads.pop()
                } else {
                    null
                }
            }
        }

        if (playThread != null) {
            playThread?.interrupt()
        }

        decodedRadioUrl = ""

        if (exoPlayer != null) {
            if (exoPlayer.isPlaying) {
                exoPlayer.stop()
            }
            updatePlayerStatus("stopped")
        }

        decodeRadioUrlThreadId = -1
        requestAudioFocusId = -2
        isInPlayingProcess = false
    }

    private fun getDecodeRadioUrlThreadId(): Int {
        return decodeRadioUrlThreadId
    }

    fun getPlayingRadio(): Radio? {
        return playingRadio
    }

    fun getRadios(): ArrayList<Radio> {
        return radios
    }

//    fun getPlayerStatusList(): java.util.ArrayList<String> {
//        return playerStatusList
//    }

    fun getPlayerStatus(): String {
        return playerStatus
    }

    private fun getRadioStreamUrl(playCZurl: String): Thread {
        decodedRadioUrl = ""
        val localDecodeRadioUrlThreadId = Math.round(Math.random() * 10000).toInt()
        decodeRadioUrlThreadId = localDecodeRadioUrlThreadId

        val one: Thread = object : Thread() {
            override fun run() {
                var continueDownloading = true
                while (continueDownloading && getDecodeRadioUrlThreadId() == localDecodeRadioUrlThreadId) {
                    val result = StringBuilder()

                    var urlConnection: HttpURLConnection? = null

                    try {
                        var startTime = System.currentTimeMillis()

                        val url = URL(playCZurl)
                        urlConnection = url.openConnection() as HttpURLConnection
                        urlConnection.requestMethod = "GET"
                        urlConnection.setRequestProperty("Content-length", "0")
                        urlConnection.setRequestProperty("Content-type", "Application/JSON")
                        urlConnection.useCaches = false
                        urlConnection.allowUserInteraction = false
                        urlConnection.connectTimeout = 5000
                        urlConnection.readTimeout = 5000
                        urlConnection.doOutput = true
                        urlConnection.connect()

                        JSONdownloadResponseTime = System.currentTimeMillis() - startTime
                        Log.v(LOG_TAG, "JSON downloading took ${JSONdownloadResponseTime}ms")
                        notifyHandlers("JSON_DOWNLOADED", JSONdownloadResponseTime.toString())

                        if (urlConnection.responseCode == HttpURLConnection.HTTP_OK) {
                            val scanner = Scanner(urlConnection.getInputStream())
                            while (scanner.hasNext()) {
                                result.append(scanner.nextLine())
                            }
                            scanner.close()

                            val jsonObject = JSONObject(result.toString())

                            if (getDecodeRadioUrlThreadId() == decodeRadioUrlThreadId) {
                                decodedRadioUrl = jsonObject.getString("redir")
                            }

                            continueDownloading = false
                        }
                    } catch (e: java.lang.Exception) {
                        Log.e(LOG_TAG, "getRadioStreamUrl: " + e.message)
                        try {
                            sleep(2000)
                        } catch (ex: java.lang.Exception) {
                        }
                    } finally {
                        urlConnection?.disconnect()
                    }
                }
            }
        }
        one.start()


        val two: Thread = object : Thread() {
            override fun run() {
                while (decodedRadioUrl.length == 0 && getDecodeRadioUrlThreadId() == decodeRadioUrlThreadId) {
                    yield()
                }

                Log.v(LOG_TAG, "decodedRadioUrl done ${decodedRadioUrl}")
            }
        }
        two.start()

        decodedRadioUrlThreads.push(two)

        return two
    }

    fun stopAndSaveState() {
        stop()
        val activitySettings = getSharedPreferences("RadiosActivity", MODE_PRIVATE)
        val activitySettingsEdit = activitySettings.edit()
        activitySettingsEdit.putString("playingRadioName", "")
        activitySettingsEdit.putInt("playingRadioUrlIndex", 0)
        activitySettingsEdit.commit()
        activitySettingsEdit.apply()
    }

    private fun notifyHandlers(messageType: String, data: String?) {
        val intent = Intent("from-radio-player-service")
        intent.putExtra("messageName", messageType)
        if (!data.isNullOrEmpty()) {
            intent.putExtra("data", data)
        }

        EventBus.getDefault().post(MessageEvent(intent));
    }


    fun destroy() {
        stop()

        if (exoPlayer != null) {
            exoPlayer.release()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        EventBus.getDefault().unregister(this);
    }
}