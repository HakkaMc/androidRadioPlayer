package com.example.androidradioplayer

import android.content.Intent
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.media.MediaBrowserServiceCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.exoplayer.ExoPlayer
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Scanner


private const val MY_MEDIA_ROOT_ID = "media_root_id"
private const val MY_EMPTY_MEDIA_ROOT_ID = "empty_root_id"

class MyMediaBrowserService : MediaBrowserServiceCompat() {
    private val LOG_TAG = "MyMediaBrowserService"

    private var mediaSession: MediaSessionCompat? = null

    private lateinit var stateBuilder: PlaybackStateCompat.Builder

    private val radios = ArrayList<Radio>()

    private var radioIndex: Int = 0

    private lateinit var exoPlayer: ExoPlayer

    override fun onCreate() {
        super.onCreate()

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

        stateBuilder = PlaybackStateCompat.Builder()

        exoPlayer = ExoPlayer.Builder(this).build()

        // Create a MediaSessionCompat
        mediaSession = MediaSessionCompat(baseContext, LOG_TAG).apply {

            // Enable callbacks from MediaButtons and TransportControls
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
                        or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )

            // Set an initial PlaybackState with ACTION_PLAY, so media buttons can start the player
            stateBuilder = PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY
                            or PlaybackStateCompat.ACTION_PLAY_PAUSE
                )
            setPlaybackState(stateBuilder.build())

            // MySessionCallback() has methods that handle callbacks from a media controller
//            setCallback(MySessionCallback())

            // Set the session's token so that client activities can communicate with it.
            setSessionToken(sessionToken)

        }

        mediaSession!!.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                super.onPlay()

                val radio = radios[radioIndex]

                play(radio)

            }

            override fun onPause() {
                super.onPause()

                if (exoPlayer.isPlaying) {
                    exoPlayer.pause()
                }

                stateBuilder.setState(
                    PlaybackStateCompat.STATE_PAUSED,
                    0,
                    0.0F
                )
                    .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
                mediaSession!!.setPlaybackState(stateBuilder.build())
            }

            override fun onStop() {
                super.onStop()
            }

            override fun onSkipToNext() {
                super.onSkipToNext()

                val index = (radioIndex + 1) % radios.size
                val radio = radios[index]

                radioIndex = index
                play(radio)
            }

            override fun onFastForward() {
                super.onFastForward()
            }

            override fun onSkipToPrevious() {
                super.onSkipToPrevious()

                var index = radioIndex - 1
                if (index < 0) {
                    index = radios.size - 1
                }
                val radio = radios[index]

                radioIndex = index
                play(radio)
            }

            override fun onPrepareFromMediaId(mediaId: String?, extras: Bundle?) {
                super.onPrepareFromMediaId(mediaId, extras)
            }

            override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
                super.onPlayFromMediaId(mediaId, extras)

                val radio = radios.find { r -> r.getName().equals(mediaId) }

                if (radio != null) {
                    radioIndex = radios.indexOf(radio)
                    play(radio)
                }
            }

            override fun onPlayFromUri(uri: Uri?, extras: Bundle?) {
                super.onPlayFromUri(uri, extras)
            }

        })

        mediaSession!!.isActive = true
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): MediaBrowserServiceCompat.BrowserRoot {

        // (Optional) Control the level of access for the specified package name.
        // You'll need to write your own logic to do this.
//        return if (allowBrowsing(clientPackageName, clientUid)) {
//            // Returns a root ID that clients can use with onLoadChildren() to retrieve
//            // the content hierarchy.
//            MediaBrowserServiceCompat.BrowserRoot(MY_MEDIA_ROOT_ID, null)
//        } else {
        // Clients can connect, but this BrowserRoot is an empty hierarchy
        // so onLoadChildren returns nothing. This disables the ability to browse for content.
        return MediaBrowserServiceCompat.BrowserRoot(MY_EMPTY_MEDIA_ROOT_ID, null)
//        }
    }

    override fun onLoadChildren(
        parentMediaId: String,
        result: MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>>
    ) {
        //  Browsing not allowed
//        if (MY_EMPTY_MEDIA_ROOT_ID == parentMediaId) {
//            result.sendResult(null)
//            return
//        }

        // Assume for example that the music catalog is already loaded/cached.

//        val mediaItems = emptyList<MediaBrowserCompat.MediaItem>()


        val mediaItems = ArrayList<MediaBrowserCompat.MediaItem>()

        // Check if this is the root menu:
//        if (MY_MEDIA_ROOT_ID == parentMediaId) {
        // Build the MediaItem objects for the top level,
        // and put them in the mediaItems list...

        radios.forEach { radio ->
//            val drawableResourceId = resources.getIdentifier(
//                "drawable/"+radio.getIconName(), null,
//                packageName
//            )


//            val filePath = applicationContext.resources.getResourceEntryName(drawableResourceId)
//            val drawableUri =
//                FileProvider.getUriForFile(this, "com.example.androidradioplayer", File(filePath))

//            val uri = Uri.parse("android.resource://" + packageName + "/" + radio.getIconName())
//            val uri = Uri.parse("android.resource://drawable/" + radio.getIconName()+".png")

//            val imageUri = Uri.Builder()
//                .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
//                .authority(resources.getResourcePackageName(drawableResourceId))
//                .appendPath(resources.getResourceEntryName(drawableResourceId)).build()


//            val filePath = applicationContext.resources.getResourceEntryName(drawableResourceId)
//            val drawableUri =
//                FileProvider.getUriForFile(this, packageName, File(filePath))
//
//            val drawable = resources.getDrawable(drawableResourceId)


//            Log.v(LOG_TAG, "onLoadChildren: "+imageUri.toString())


//            val drawable = resources.getDrawable(drawableResourceId)
//            drawable.getUri
//

//            val mediaUri = Uri.parse(radio.getFirstUrl().url)
//            val mediaUri = Uri.parse("http://icecast9.play.cz/kiss128.mp3")

//            val extras = Bundle()
//            extras.putStringArrayList(
//                "DESCRIPTION_EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ID_LIST",
//                radio.getName()
//            )

            val descriptionCompat = MediaDescriptionCompat.Builder()
                .setMediaId(radio.getName())
                .setTitle(radio.getName())
//                .setSubtitle(System.currentTimeMillis().toString())
//                .setDescription(System.currentTimeMillis().toString())
//                .setIconUri(imageUri)
//                .setMediaUri(mediaUri)
//                .setExtras(extras)
                .build()


//            val glide = Glide.with(this)
//                .load(drawableResourceId) // Replace with your drawable resource ID
//
//            glide.into(descriptionCompat)


            val item = MediaBrowserCompat.MediaItem(
                descriptionCompat,
                MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
            )

            mediaItems.add(item)

        }


//        } else {
//            Log.v(LOG_TAG, "onLoadChildren")
        // Examine the passed parentMediaId to see which submenu we're at,
        // and put the children of that menu in the mediaItems list...
//        }
        result.sendResult(mediaItems)
    }


    override fun onLoadItem(itemId: String?, result: Result<MediaBrowserCompat.MediaItem>) {
        super.onLoadItem(itemId, result)
    }

    override fun onCustomAction(action: String, extras: Bundle?, result: Result<Bundle>) {
        super.onCustomAction(action, extras, result)
    }

    private fun getRadioStreamUrl(radio: Radio): Thread {
        val one: Thread = object : Thread() {
            override fun run() {
                if (radio.getDecodedUrls()[0].isEmpty()) {
                    if (radio.getType() === "infomaniak") {
                        val decodedRadioUrl = radio.getUrls()[0].url
                        radio.setDecodedUrl(decodedRadioUrl, 0)
                    } else {
                        var urlConnection: HttpURLConnection? = null

                        try {
                            var decodedRadioUrl = ""
                            val result = StringBuilder()

                            val url = URL(radio.getFirstUrl().url)
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
                                val scanner = Scanner(urlConnection.getInputStream())
                                while (scanner.hasNext()) {
                                    result.append(scanner.nextLine())
                                }
                                scanner.close()

                                val jsonObject = JSONObject(result.toString())

                                decodedRadioUrl = jsonObject.getString("redir")

                                if (!decodedRadioUrl.isNullOrEmpty()) {
                                    radio.setDecodedUrl(decodedRadioUrl, 0)
                                }
                            }
                        } catch (e: java.lang.Exception) {
                            Log.e(LOG_TAG, "getRadioStreamUrl exception", e)
                        } finally {
                            urlConnection?.disconnect()
                        }
                    }
                }
            }
        }
        one.start()

        return one
    }

    private fun play(radio: Radio): Thread {
        val thread = object : Thread() {
            override fun run() {
                getRadioStreamUrl(radio).join()

                val decodedUrl = radio.getDecodedUrls()[0]

                if (decodedUrl.isNotEmpty()) {
                    val mediaItem = MediaItem.fromUri(decodedUrl)

                    val handler = Handler(exoPlayer.applicationLooper)

                    handler.post(Runnable {
                        exoPlayer.setMediaItem(mediaItem)
                        exoPlayer.prepare()
                        exoPlayer.play()

                        stateBuilder.setState(
                            PlaybackStateCompat.STATE_PLAYING,
                            0,
                            0.0F
                        )
                            .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
                        mediaSession!!.setPlaybackState(stateBuilder.build())

                        val metadataBuilder = MediaMetadataCompat.Builder()
                        metadataBuilder.putText(
                            MediaMetadataCompat.METADATA_KEY_TITLE,
                            radio.getName()
                        )

                        mediaSession!!.setMetadata(metadataBuilder.build())
                    })
                }
            }
        }
        thread.start()

        return thread
    }

}