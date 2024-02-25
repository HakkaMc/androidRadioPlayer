package com.example.androidcarhelper2

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat

class RadioGridAdapter(private var context: Context?, private var radios: ArrayList<Radio>) : BaseAdapter() {
    private var selectedRadioIndex = -1
    private var radioStatus = ""
    private val LOG_TAG = "RadioGridAdapter"

    fun setSelectedRadioIndex(index: Int) {
        selectedRadioIndex = index
    }

    fun setRadioStatus(status: String) {
        radioStatus = status
    }

    override fun getCount(): Int {
        return radios.size
    }

    override fun getItemId(i: Int): Long {
        return i.toLong()
    }

    override fun getItem(i: Int): Radio {
        return radios[i]
    }

    fun getItemByName(radioName: String?): Radio? {

        var radio: Radio? = null
        for (i in radios.indices) {
            val tmpRadio = radios[i]
            if (tmpRadio.getName().equals(radioName)) {
                radio = tmpRadio
                break
            }
        }
        return radio
    }

    fun getItemIndexByName(radioName: String?): Int {
        var index = -1
        for (i in radios.indices) {
            val tmpRadio = radios[i]
            if (tmpRadio.getName().equals(radioName)) {
                index = i
                break
            }
        }
        return index
    }

    fun getItems(): ArrayList<*>? {
        return radios
    }

    fun getItemById(id: String?): Radio? {

        var radio: Radio? = null
        if (id != null && id.length > 0) {
            for (i in radios.indices) {
                val tmp = radios[i]
                if (tmp.getId().equals(id)) {
                    radio = tmp
                    break
                }
            }
        }
        return radio
    }


    /*
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var view = convertView
        val viewHolder: ViewHolder

        // Use ViewHolder pattern for efficiency
        if (view == null) {
            view = LayoutInflater.from(context).inflate(R.layout.item_layout, parent, false)
            viewHolder = ViewHolder(view)
            view.tag = viewHolder
        } else {
            viewHolder = view.tag as ViewHolder
        }

        val item = getItem(position)

        // Load image using Glide for better performance and placeholder
        Glide.with(context)
                .load(item.image)
                .placeholder(R.drawable.placeholder_image) // Add placeholder image
                .into(viewHolder.imageView)

        viewHolder.textView.text = item.name

        return view
    }
     */
    override fun getView(i: Int, view: View?, viewGroup: ViewGroup?): View? {
        Log.v(LOG_TAG, "getView: ${i}, radio index: ${selectedRadioIndex}")

        if(context !== null) {
            val inflater = LayoutInflater.from(context)

            val item: View = inflater.inflate(R.layout.radio_item, null)
            val radioStatusView = item.findViewById<View>(R.id.radio_status) as TextView

            if (context !== null) {
                if (i == selectedRadioIndex) {
                    item.setBackgroundColor(
                        ContextCompat.getColor(
                            context!!,
                            android.R.color.black
                        )
                    )
                    radioStatusView.text = radioStatus
                } else {
                    item.setBackgroundColor(ContextCompat.getColor(context!!, R.color.grey2))
                    radioStatusView.text = ""
                }
            }

            try {
                val imgView = item.findViewById<View>(R.id.image_source) as ImageView
                val resourceId = context!!.resources.getIdentifier(
                    "drawable/" + radios[i].getIconName(),
                    null,
                    context!!.packageName
                )
                imgView.setImageResource(resourceId)
            } catch (ex: Exception) {
                println("getView exception: $ex")
            }
            return item
        }
        return view
    }
}