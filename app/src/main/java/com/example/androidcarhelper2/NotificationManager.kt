package com.example.androidcarhelper2

import android.content.Intent

class NotificationManager private constructor() {
    companion object{
        private var instance: NotificationManager? = null

        fun getInstance() = instance ?: synchronized(this){
            instance ?: NotificationManager().also { instance = it }
        }
    }

    private val notificationLiveData = NotificationLiveData()

    public fun sendNotificationMessage(message: Intent) {
        notificationLiveData.sendNotification(message)
    }

    public fun getNotificationLiveData(): NotificationLiveData? {
        return notificationLiveData
    }
}