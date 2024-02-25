package com.example.androidradioplayer

import android.content.Intent
import androidx.lifecycle.LiveData

class NotificationLiveData: LiveData<Intent>() {
    public fun sendNotification(message: Intent){
        postValue(message)
    }
}