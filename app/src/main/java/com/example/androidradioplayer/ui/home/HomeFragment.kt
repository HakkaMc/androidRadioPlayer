package com.example.androidradioplayer.ui.home

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.androidradioplayer.MediaSessionService
import com.example.androidradioplayer.MessageEvent
import com.example.androidradioplayer.R
import com.example.androidradioplayer.Radio
import com.example.androidradioplayer.RadioGridAdapter
import com.example.androidradioplayer.RadioPlayerService
import com.example.androidradioplayer.databinding.FragmentHomeBinding
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import java.net.InetAddress


class HomeFragment : Fragment() {
    private val LOG_TAG = "HomeFragment"

    private var _binding: FragmentHomeBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding

    private var radioStatusLogs = ArrayList<String>()

    private var urlToPlayIndex = 0

    private var radioGridAdapter: RadioGridAdapter? = null

    private var radioPlayerService: RadioPlayerService? = null

    private var mediaSessionService: MediaSessionService? = null

    private var checkInternetConnectionThread: Thread? = null

    private var mediaSessionServiceConnection: ServiceConnection? = null

    private var radioPlayerServiceConnection: ServiceConnection? = null

    private var radioStatusLogsAdapter = object : BaseAdapter() {
        override fun getCount(): Int {
            return radioStatusLogs.size
        }

        override fun getItem(i: Int): Any {
            return radioStatusLogs[i]
        }

        override fun getItemId(i: Int): Long {
            return i.toLong()
        }

        override fun getView(i: Int, view: View, viewGroup: ViewGroup): View {
            if (binding != null) {
                val inflater = LayoutInflater.from(binding!!.root.context)

                var vi = view
                if (view == null) {
                    vi = inflater.inflate(android.R.layout.simple_list_item_1, null)
                    vi.setPadding(0, 0, 0, 0)
                }
                val tv = vi.findViewById<View>(android.R.id.text1) as TextView
                tv.text = radioStatusLogs[i]
                tv.setTextColor(resources.getColor(android.R.color.white))
                tv.setPadding(0, 0, 0, 0)
                return vi
            }

            return view
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val homeViewModel =
            ViewModelProvider(this).get(HomeViewModel::class.java)

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding!!.root


//        val textView: TextView = binding.textHome
//        homeViewModel.text.observe(viewLifecycleOwner) {
//            textView.text = it
//        }


        mediaSessionServiceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                mediaSessionService = (service as MediaSessionService.LocalBinder).getService()
                loadAndPlaySavedRadio()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
            }
        }

        val mediaSessionServiceIntent = Intent(root.context, MediaSessionService::class.java)
        root.context.bindService(
            mediaSessionServiceIntent,
            mediaSessionServiceConnection as ServiceConnection, Context.BIND_AUTO_CREATE
        )


        radioPlayerServiceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                radioPlayerService = (service as RadioPlayerService.LocalBinder).getService()

                initGridView()

                loadAndPlaySavedRadio()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
            }
        }

        val radioPlayerServiceIntent = Intent(root.context, RadioPlayerService::class.java)
        root.context.bindService(
            radioPlayerServiceIntent,
            radioPlayerServiceConnection as ServiceConnection, Context.BIND_AUTO_CREATE
        )

        root.findViewById<LinearLayout>(R.id.info_vertical_layout)?.setOnClickListener {
            val url = "https://waze.com/ul?"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        }

        checkInternetAccess()

        return root
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this);
    }

    // Hack due to Android 7.1
    override fun onResume() {
        super.onResume()

        updateGridviewColors()
    }
    @Subscribe
    public fun onEvent(event: MessageEvent) {
        Log.v(LOG_TAG, "onEvent: ${event.getMessageName()}")
        val it = event.message

        val messageName = event.getMessageName()

        if (it != null) {
            when (messageName) {
                "RADIO_STATUS_CHANGED" -> {
                    updateInfo()
                    updateGridviewColors()
                }

                "RADIO_SOURCE_CHANGED" -> {
                    updateGridviewColors()
                }

                "JSON_DOWNLOADED" -> {
                    val data = it.getStringExtra("data")

                    // A wrap hack due to Android 7.1
                    requireActivity().runOnUiThread {
                        binding?.root?.findViewById<TextView>(R.id.jsonDownloadTime)?.text =
                            "${data} ms"
                    }

                    Log.v(LOG_TAG, "JSON_DOWNLOADED after ${data} ms")
                }

                "PLAYER_PREPARED" -> {
                    val data = it.getStringExtra("data")

                    // A wrap hack due to Android 7.1
                    requireActivity().runOnUiThread {
                        binding?.root?.findViewById<TextView>(R.id.playerPreparedTime)?.text =
                            "${data} ms"
                    }

                    Log.v(LOG_TAG, "PLAYER_PREPARED after ${data} ms")
                }
            }
        }
    }

    private fun loadAndPlaySavedRadio() {
        if (radioPlayerService != null && mediaSessionService != null && binding != null) {
            val activitySettings = binding!!.root.context.getSharedPreferences(
                "RadiosActivity",
                Context.MODE_PRIVATE
            )

            val playingRadioName = activitySettings.getString("playingRadioName", "")
            val playingRadioUrlIndex = activitySettings.getInt("playingRadioUrlIndex", -1)

            Log.d(
                LOG_TAG,
                "autoplay radio: radio name $playingRadioName, url index $playingRadioUrlIndex"
            )

            if (playingRadioUrlIndex > -1 && playingRadioName != "" && !radioPlayerService?.isPlaying()!!) {
                val radio =
                    radioGridAdapter!!.getItemByName(
                        activitySettings.getString(
                            "playingRadioName",
                            ""
                        )
                    )

                if (radio != null) {
                    mediaSessionService?.refreshMediaSession(context)
                    radioPlayerService?.play(radio, playingRadioUrlIndex)
                } else {
                    Log.d(LOG_TAG, "autoplay radio not found")
                }

            }
        }
    }

    private fun initGridView() {
        if (radioPlayerService != null && binding != null) {
            radioGridAdapter =
                RadioGridAdapter(binding!!.root.context, radioPlayerService!!.getRadios())

            var gridView = getGridView()
            if (gridView !== null) {
                gridView.adapter = radioGridAdapter

                gridView.setOnItemClickListener { adapterView, view, i, l ->
                    Log.v(LOG_TAG, "Grid item click position: ${i}")


//                    stopPlayer()

                    if (radioPlayerService != null) {
                        val tmpRadio = adapterView.getItemAtPosition(i) as Radio?
                        var radio = radioPlayerService?.getPlayingRadio() as Radio?

                        if (tmpRadio != null && radio != null) {
                            Log.d(
                                "RadiosActivity",
                                "change radio: " + radio.getName() + " vs " + tmpRadio.getName()
                            )
                            if (radio.getId().equals(tmpRadio.getId())) {
                                radio = tmpRadio
                                if (radioPlayerService!!.isPlaying()) {
                                    println("Stop")
                                    radioPlayerService?.stop()
                                    urlToPlayIndex = 0
                                    saveRadioToActivitySettings("", 0)
                                } else {
                                    println("Continue")
                                    mediaSessionService?.refreshMediaSession(context)
                                    radioPlayerService!!.play(radio, urlToPlayIndex)
                                    saveRadioToActivitySettings(radio.getName(), urlToPlayIndex)
                                }
                            } else {
                                println("Change radio")
                                radio = tmpRadio
                                mediaSessionService?.refreshMediaSession(context)
                                radioPlayerService!!.play(radio, 0)
                                urlToPlayIndex = 0
                                saveRadioToActivitySettings(radio.getName(), 0)
                            }
                        } else if (tmpRadio != null) {
                            println("Brand new radio")
                            radio = tmpRadio
                            mediaSessionService?.refreshMediaSession(context)
                            radioPlayerService?.play(radio, i)
                            urlToPlayIndex = i
                            saveRadioToActivitySettings(radio.getName(), i)
                        }
                    }

                    updateGridviewColors()

                    true
                }

                gridView.setOnItemLongClickListener { parent, view, position, id ->
                    if (radioPlayerService != null) {
                        var popupMenu = PopupMenu(view.context, view)

                        val radio = radioPlayerService!!.getPlayingRadio()
                        if (radio != null) {
                            for (i in 0 until radio.getUrls().size) {
                                popupMenu.menu.add(i, i, i, radio.getUrls().get(i).description)
                            }
                        }


                        popupMenu.setOnMenuItemClickListener { item ->
                            var orderPosition = item.itemId

                            val radio = radioPlayerService!!.getPlayingRadio()
                            if (radio != null) {
                                radioPlayerService!!.play(radio, orderPosition)
                                saveRadioToActivitySettings(radio.getName(), orderPosition)
                            }

                            true
                        }

                        popupMenu.show()
                    }

                    true
                }
            } else {
                throw Error("HomeFragment - gridView not found!")
            }
        }
    }

    private fun getGridView(): GridView? {
        if (binding != null) {
            val root: View = binding!!.root

            return root.findViewById<GridView>(R.id.grid_view)
        }

        return null
    }

    private fun getGridAdapter(): RadioGridAdapter? {
        return radioGridAdapter
    }

    private fun updateInfo() {
        if (radioPlayerService != null) {
            radioStatusLogs = radioPlayerService?.getPlayerStatusList()!!
        }
        radioStatusLogsAdapter.notifyDataSetChanged()
    }

    private fun updateGridviewColors() {
        val gridView = getGridView()
        val ga = getGridAdapter()

        if (gridView != null && radioPlayerService != null) {
            val tmpRadio = radioPlayerService!!.getPlayingRadio()
            if (tmpRadio != null && ga != null) {
                var radioName = tmpRadio.getName()
                val radioIndex: Int = ga.getItemIndexByName(radioName)
                ga.setSelectedRadioIndex(radioIndex)
                ga.setRadioStatus(radioPlayerService!!.getPlayerStatus())
                gridView.smoothScrollToPosition(radioIndex)
                gridView.invalidateViews()
            } else {
                Log.d("RadiosActivity", "updateGridviewColors: radio objekt je null")
            }
        } else {
            Log.d(
                "RadiosActivity",
                "updateGridviewColors: gridView nebo radioPlayerService je null"
            )
        }
    }

    private fun stopPlayer() {
        if (radioPlayerService != null && radioPlayerService!!.isPlaying()) {
            radioPlayerService!!.stop()
        }
    }

    private fun saveRadioToActivitySettings(radioName: String, playingUrlIndex: Int) {
        if (binding != null) {
            val activitySettings =
                binding!!.root.context.getSharedPreferences("RadiosActivity", Context.MODE_PRIVATE)

            Log.v("RadiosActivity", "saveRadioToActivitySettings: radio name $radioName")
            val activitySettingsEdit: SharedPreferences.Editor = activitySettings.edit()
            activitySettingsEdit.putString("playingRadioName", radioName)
            activitySettingsEdit.putInt("playingRadioUrlIndex", playingUrlIndex)
            activitySettingsEdit.commit()
            activitySettingsEdit.apply()
        }
    }

    private fun checkInternetAccess() {
        if (checkInternetConnectionThread == null || checkInternetConnectionThread?.isInterrupted() == true) {
            checkInternetConnectionThread = object : Thread() {
                override fun run() {
                    var internetAccess = false

                    while (true) {
                        var responseTime: Long = 0

                        // A wrap hack due to Android 7.1
                        requireActivity().runOnUiThread {
                            binding?.root?.findViewById<TextView>(R.id.internetResponseTime)?.text =
                                "..."
                        }

                        try {
                            var startTime = System.currentTimeMillis()
                            //val inetAddress = InetAddress.getByName("8.8.8.8")
                            val inetAddress = InetAddress.getByName("google.com")

                            internetAccess = inetAddress.isReachable(5000)

                            responseTime = System.currentTimeMillis() - startTime

                            Log.v(
                                LOG_TAG,
                                "internetAccess: ${internetAccess.toString()} (${responseTime})ms"
                            )

                        } catch (ex: Exception) {
                            internetAccess = false

                            Log.e("WidgetInfoService", "checkInternetAccess", ex)
                        } finally {
                            var stringColor = "#FF0000"

                            if (internetAccess == true) {
                                stringColor = "#00A823"
                            }

                            val color = Color.parseColor(stringColor)

                            val colorStateList = ColorStateList.valueOf(color)

                            // A wrap hack due to Android 7.1
                            requireActivity().runOnUiThread {
                                binding?.root?.findViewById<ImageView>(R.id.ic_network_check)?.imageTintList =
                                    colorStateList

                                binding?.root?.findViewById<TextView>(R.id.internetResponseTime)?.text =
                                    "${responseTime} ms"
                            }
                        }

                        try {
                            if (internetAccess) {
                                sleep(10000)
                            } else {
                                sleep(2000)
                            }
                        } catch (ex: Exception) {
                        }
                    }
                }
            }
            (checkInternetConnectionThread as Thread).start()
        }
    }

    override fun onStop() {
        EventBus.getDefault().unregister(this);
        super.onStop()
    }

    override fun onDestroyView() {
        checkInternetConnectionThread?.interrupt()

        if (radioPlayerServiceConnection != null && radioPlayerService != null) {
            radioPlayerService?.stop()

            binding?.root?.context?.unbindService(radioPlayerServiceConnection!!)
        }

        if (mediaSessionServiceConnection != null) {
            binding?.root?.context?.unbindService(mediaSessionServiceConnection!!)
        }

        _binding = null

        super.onDestroyView()
    }
}