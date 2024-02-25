package com.example.androidcarhelper2

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Binder
import android.util.Log
import androidx.lifecycle.Observer
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Stack

class RadioPlayerService() : Service() {
    private val LOG_TAG = "RadioPlayerService"

    val RADIO_STATUS_CHANGED = "RADIO_STATUS_CHANGED"

    val RADIO_SOURCE_CHANGED = "RADIO_SOURCE_CHANGED"

    private val radios = ArrayList<Radio>()

    private val decodedRadioUrlThreads = Stack<Thread>()

    private var audioManager: AudioManager? = null

    private var player: MediaPlayer = MediaPlayer()

    private var requestAudioFocusId: Long = -2 // -1 granted; -2 not granted

    private var playingRadio: Radio? = null

    private var playerStatus = ""

    private var playingUrlIndex = 0

    private var isInPlayingProcess = false

    private var playerStatusList = java.util.ArrayList<String>()

    private var decodedRadioUrl = ""

    private var decodeRadioUrlThreadId = -1

    private var playThread: Thread? = null

    private val notificationObserver: Observer<Intent> = Observer() {
        val messageName = it.getStringExtra("messageName")

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
                    Log.v("RadioPlayerService", "AUDIOFOCUS_REQUEST_GRANTED")
                    updatePlayerStatus("AUDIOFOCUS_REQUEST_GRANTED ")
                } else {
                    requestAudioFocusId = -2
                    Log.v("RadioPlayerService", "AUDIOFOCUS_REQUEST_FAILED")
                    updatePlayerStatus("AUDIOFOCUS_REQUEST_FAILED ")
                }
            }

            "AUDIOFOCUS_REQUEST_FAILED" -> {
                requestAudioFocusId = -2
                Log.v("RadioPlayerService", "AUDIOFOCUS_REQUEST_FAILED")
                updatePlayerStatus("AUDIOFOCUS_REQUEST_FAILED ")
            }

            "AUDIOFOCUS_LOSS" -> {
                requestAudioFocusId = -2
                Log.v("RadioPlayerService", "AUDIOFOCUS_LOSS")
                updatePlayerStatus("AUDIOFOCUS_LOSS ")
                stopAndSaveState()
            }

            "KEYCODE_MEDIA_NEXT" -> {
                Log.v("RadioPlayerService", "KEYCODE_MEDIA_NEXT")
                updatePlayerStatus("KEYCODE_MEDIA_NEXT ")
                if (isInPlayingProcess) {
                    nextRadio()
                }
            }

            "KEYCODE_MEDIA_PREVIOUS" -> {
                Log.v("RadioPlayerService", "KEYCODE_MEDIA_PREVIOUS")
                updatePlayerStatus("KEYCODE_MEDIA_PREVIOUS ")
                if (isInPlayingProcess) {
                    previousRadio()
                }
            }

            "KEYCODE_MEDIA_PAUSE" -> {
                Log.v("RadioPlayerService", "KEYCODE_MEDIA_PAUSE")
                updatePlayerStatus("KEYCODE_MEDIA_PAUSE ")

                if (isInPlayingProcess) {
                    stop()
                }
            }

            "KEYCODE_MEDIA_PLAY" -> {
                Log.v("RadioPlayerService", "KEYCODE_MEDIA_PLAY")
                updatePlayerStatus("KEYCODE_MEDIA_PLAY ")

                if (!isInPlayingProcess && playingRadio != null) {
                    play(playingRadio!!, playingUrlIndex)
                }
            }
        }
    }

    override fun onBind(intent: Intent) = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService() = this@RadioPlayerService
    }

    init {
        var radio = Radio("Kiss 98", "kiss98", "play")
        radio.addUrl("http://api.play.cz/json/getStream/kiss/mp3/128", "128")
        radio.addUrl("http://api.play.cz/json/getStream/kiss/mp3/64", "64")
        radios.add(radio)

        radio = Radio("Beat", "beat", "play")
        radio.addUrl("http://api.play.cz/json/getStream/beat/mp3/128", "128")
        radio.addUrl("http://api.play.cz/json/getStream/beat/mp3/64", "64")
        radios.add(radio)

        radio = Radio("Hey", "hey", "play")
        radio.addUrl("http://api.play.cz/json/getStream/hey-radio/mp3/128", "128")
        radio.addUrl("http://api.play.cz/json/getStream/hey-radio/mp3/64", "64")
        radios.add(radio)

        radio = Radio("Expres FM", "expresfm", "play")
        radio.addUrl("http://api.play.cz/json/getStream/expres/mp3/128", "128")
        radio.addUrl("http://api.play.cz/json/getStream/expres/mp3/64", "64")
        radios.add(radio)

        radio = Radio("Rock Zone", "rockzone", "play")
        radio.addUrl("http://api.play.cz/json/getStream/rockzone/mp3/128", "128")
        radio.addUrl("http://api.play.cz/json/getStream/rockzone/mp3/64", "64")
        radios.add(radio)

        radio = Radio("Rádio Čas Rock", "casrock", "play")
        radio.addUrl("http://api.play.cz/json/getStream/casrock/mp3/128", "128")
        radio.addUrl("http://api.play.cz/json/getStream/casrock/mp3/64", "64")
        radios.add(radio)

        radio = Radio("RFI", "rfi", "infomaniak")
        radio.addUrl("https://live-reflector.ice.infomaniak.ch/rfimonde-64.mp3", "64")
        radios.add(radio)
    }

    override fun onCreate() {
        super.onCreate()

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager?

        NotificationManager.getInstance().getNotificationLiveData()
            ?.observeForever(notificationObserver)

        player.setOnInfoListener { mediaPlayer, what, extra ->
            Log.v(LOG_TAG, "setOnInfoListener: ${what}")

            when (what) {
                MediaPlayer.MEDIA_INFO_BUFFERING_START -> {
                    Log.d(
                        "RadioPlayerService",
                        "MEDIA_INFO_BUFFERING_START"
                    )

                    updatePlayerStatus("buffering_start")
                }

                MediaPlayer.MEDIA_INFO_BUFFERING_END -> {
                    Log.d(
                        "RadioPlayerService",
                        "MEDIA_INFO_BUFFERING_END"
                    )

                    updatePlayerStatus("buffering_end")
                }

                MediaPlayer.MEDIA_INFO_METADATA_UPDATE -> {
                    Log.d(
                        "RadioPlayerService",
                        "MEDIA_INFO_METADATA_UPDATE"
                    )
                    updatePlayerStatus("metadata_update")
                }

                MediaPlayer.MEDIA_INFO_NOT_SEEKABLE -> {
                    Log.d(
                        "RadioPlayerService",
                        "MEDIA_INFO_NOT_SEEKABLE"
                    )
                }

                MediaPlayer.MEDIA_INFO_BAD_INTERLEAVING -> {
                    Log.d(
                        "RadioPlayerService",
                        "MEDIA_INFO_BAD_INTERLEAVING"
                    )
                }

                MediaPlayer.MEDIA_INFO_AUDIO_NOT_PLAYING -> {
                    Log.d(
                        "RadioPlayerService",
                        "MEDIA_INFO_AUDIO_NOT_PLAYING"
                    )
                }

                MediaPlayer.MEDIA_INFO_UNKNOWN -> {
                    Log.d(
                        "RadioPlayerService",
                        "MEDIA_INFO_UNKNOWN"
                    )
                }

                else -> {
                    Log.d("RadioPlayerService", "Unknown info state: $what")
                }
            }

            false
        }

        player.setOnCompletionListener {
            Log.d("RadioPlayerService", "onCompletion")

            if (player != null) {
                if (player.isPlaying) {
                    player.stop()
                }

                player.reset()

                if (playingRadio != null) {
                    play(playingRadio!!, playingUrlIndex)
                } else {
                    updatePlayerStatus("track_end")
                }
            } else {
                updatePlayerStatus("track_end")
            }
        }

        player.setOnErrorListener { mediaPlayer, i, i1 ->
            Log.d("RadioPlayerService", "Player error $i / $i1")

            when (i) {
                MediaPlayer.MEDIA_ERROR_UNKNOWN -> Log.e(
                    "RadioPlayerService",
                    "player.setOnErrorListener - MEDIA_ERROR_UNKNOWN"
                )

                MediaPlayer.MEDIA_ERROR_SERVER_DIED -> Log.e(
                    "RadioPlayerService",
                    "player.setOnErrorListener - MEDIA_ERROR_SERVER_DIED"
                )

                MediaPlayer.MEDIA_ERROR_IO -> Log.e(
                    "RadioPlayerService",
                    "player.setOnErrorListener - MEDIA_ERROR_IO"
                )

                MediaPlayer.MEDIA_ERROR_MALFORMED -> Log.e(
                    "RadioPlayerService",
                    "player.setOnErrorListener - MEDIA_ERROR_MALFORMED"
                )

                MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK -> Log.e(
                    "RadioPlayerService",
                    "player.setOnErrorListener - MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK"
                )

                MediaPlayer.MEDIA_ERROR_TIMED_OUT -> Log.e(
                    "RadioPlayerService",
                    "player.setOnErrorListener - MEDIA_ERROR_TIMED_OUT"
                )

                MediaPlayer.MEDIA_ERROR_UNSUPPORTED -> Log.e(
                    "RadioPlayerService",
                    "player.setOnErrorListener - MEDIA_ERROR_UNSUPPORTED"
                )
            }

            when (i1) {
                MediaPlayer.MEDIA_ERROR_UNKNOWN -> Log.e(
                    "RadioPlayerService",
                    "player.setOnErrorListener - MEDIA_ERROR_UNKNOWN"
                )

                MediaPlayer.MEDIA_ERROR_SERVER_DIED -> Log.e(
                    "RadioPlayerService",
                    "player.setOnErrorListener - MEDIA_ERROR_SERVER_DIED"
                )

                MediaPlayer.MEDIA_ERROR_IO -> Log.e(
                    "RadioPlayerService",
                    "player.setOnErrorListener - MEDIA_ERROR_IO"
                )

                MediaPlayer.MEDIA_ERROR_MALFORMED -> Log.e(
                    "RadioPlayerService",
                    "player.setOnErrorListener - MEDIA_ERROR_MALFORMED"
                )

                MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK -> Log.e(
                    "RadioPlayerService",
                    "player.setOnErrorListener - MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK"
                )

                MediaPlayer.MEDIA_ERROR_TIMED_OUT -> Log.e(
                    "RadioPlayerService",
                    "player.setOnErrorListener - MEDIA_ERROR_TIMED_OUT"
                )

                MediaPlayer.MEDIA_ERROR_UNSUPPORTED -> Log.e(
                    "RadioPlayerService",
                    "player.setOnErrorListener - MEDIA_ERROR_UNSUPPORTED"
                )
            }
            updatePlayerStatus("reset")
            mediaPlayer.reset()

            false
        }

        player.setOnSeekCompleteListener {
            Log.d("RadioPlayerService", "onSeekComplete")

            if (player != null) {
                if (player.isPlaying) {
                    player.stop()
                }

                player.reset()

                if (playingRadio != null) {
                    play(playingRadio!!, playingUrlIndex)
                } else {
                    updatePlayerStatus("seek_end")
                }
            } else {
                updatePlayerStatus("seek_end")
            }
        }

        player.setOnBufferingUpdateListener { mediaPlayer, i ->
            Log.d("RadioPlayerService", "Player buffering update $i")
            updatePlayerStatus("buffering")
        }

        player.setOnPreparedListener {
            player.start()
            updatePlayerStatus("playing")
            Log.d("RadioPlayerService", "Player prepared")
        }

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        player.setAudioAttributes(audioAttributes)
    }

    private fun updatePlayerStatus(msg: String) {
        playerStatus = msg
        playerStatusList.add(0, msg)
        if (playerStatusList.size > 50) {
            val indexFrom = 0
            val indexTo = Math.min(playerStatusList.size, 50)
            playerStatusList =
                java.util.ArrayList<String>(playerStatusList.subList(indexFrom, indexTo))
        }
        notifyHandlers(RADIO_STATUS_CHANGED, null)
    }

    fun requestAudioFocus(): Boolean {
        Log.d("RadioPlayerService", "requestAudioFocus")
        var ret = false

//        requestAudioFocusId2 = 0
        requestAudioFocusId = System.currentTimeMillis()
        val originalRequestAudioFocusId = requestAudioFocusId

        val intent = Intent("to-media-session-message")
        intent.putExtra("messageName", "REQUEST_AUDIOFOCUS")
        intent.putExtra("requestAudioFocusId", requestAudioFocusId)

        NotificationManager.getInstance().sendNotificationMessage(intent)

        val thread: Thread = object : Thread() {
            override fun run() {
                var i = 0
                while (requestAudioFocusId == originalRequestAudioFocusId && i < 50){
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
            Log.e("RadioPlayerService", "requestAudioFocus - thread.join exception: ", ex)
        }

        Log.d(
            "RadioPlayerService",
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
        Log.v("RadioPlayerService", "play")
        updatePlayerStatus("playing_requested")
        stop()

        if (player != null) {
            isInPlayingProcess = true

            playThread = object : Thread() {
                override fun run() {
                    if (requestAudioFocus()) {
                        playingRadio = radio
                        playingUrlIndex = urlToPlayIndex

                        notifyHandlers(RADIO_SOURCE_CHANGED, null)

                        Log.v("RadioPlayerService", "play - preparing player")

                        updatePlayerStatus("downloading")

                        try {
                            if (radio.getType() === "infomaniak") {
                                decodedRadioUrl = radio.getUrls()[0].url
                            }
                            else if(radio.getDecodedUrls().get(urlToPlayIndex) != "") {
                                decodedRadioUrl = radio.getDecodedUrls().get(urlToPlayIndex)
                            }
                            else {
                                getRadioStreamUrl(radio.getUrls()[urlToPlayIndex].url)?.join()
                                radio.setDecodedUrl(decodedRadioUrl, urlToPlayIndex)
                            }
                        } catch (ex: Exception) {
                            Log.e("RadioPlayerService", "play", ex)
                        }

                        Log.d(
                            "RadioPlayerService",
                            "play - radioStreamUrl: $decodedRadioUrl"
                        )

                        if (decodedRadioUrl.length > 0) {
                            updatePlayerStatus("preparing")
                            try {
                                player.setDataSource(decodedRadioUrl)
                                player.prepareAsync()
                            } catch (ex: Exception) {
                                Log.e("RadioPlayerService", "play", ex)
                                updatePlayerStatus("error")
                                isInPlayingProcess = false
                            }
                        } else {
                            updatePlayerStatus("error")
                        }
                    } else {
                        playingRadio = null
                        playingUrlIndex = -1
                        Log.d("RadioPlayerService", "Focus request not granted")
                        updatePlayerStatus("missing permission")
                        notifyHandlers(RADIO_SOURCE_CHANGED, null)
                    }
                }
            }
            getPlayThread()?.start()
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
            getPlayThread()?.interrupt()
        }
        decodedRadioUrl = ""
        try {
            if (player != null) {
                if (player.isPlaying) {
                    player.stop()
                }
                player.reset()
            }
            isInPlayingProcess = false
            updatePlayerStatus("stopped")
        } catch (ex: Exception) {
            updatePlayerStatus("stopping_exception: " + System.currentTimeMillis())
        }
        decodeRadioUrlThreadId = -1
        requestAudioFocusId = -2
    }

    private fun getPlayThread(): Thread? {
        return playThread
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

    fun getPlayerStatusList(): java.util.ArrayList<String> {
        return playerStatusList
    }

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

                        if (urlConnection.responseCode == HttpURLConnection.HTTP_OK) {
                            val `in`: InputStream = BufferedInputStream(urlConnection.inputStream)
                            val reader = BufferedReader(InputStreamReader(`in`))
                            var line: String?

                            while (reader.readLine().also { line = it } != null) {
                                result.append(line)
                            }

                            val jsonObject = JSONObject(result.toString())

                            if (getDecodeRadioUrlThreadId() == decodeRadioUrlThreadId) {
                                decodedRadioUrl = jsonObject.getString("redir")
                            }

                            continueDownloading = false
                        }
                    } catch (e: java.lang.Exception) {
                        println("decodeRadioUrlThreadId: $decodeRadioUrlThreadId")
                        Log.e("RadioPlayerService", "getRadioStreamUrl: " + e.message)
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

    private fun notifyHandlers(messageType: String, data: Any?) {
        val intent = Intent("from-radio-player-service")
        intent.putExtra("messageName", messageType)

        NotificationManager.getInstance().sendNotificationMessage(intent)
    }

    fun destroy() {
        stop()
        if (player != null) {
            player.release()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        NotificationManager.getInstance().getNotificationLiveData()
            ?.removeObserver(notificationObserver)
    }
}