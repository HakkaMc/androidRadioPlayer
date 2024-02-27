package com.example.androidradioplayer

import android.content.Intent

class MessageEvent(intent: Intent?) {
    var message: Intent? = intent

    fun getMessageName(): String {
        if(message != null){
            val messageName = message?.getStringExtra("messageName")
            return messageName!!
        }

        return ""
    }
}