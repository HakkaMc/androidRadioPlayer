package com.example.androidcarhelper2

class RadioUrl(var url: String, var description: String) {
    var id = ""

    init {
        id = "radio_url_" + System.currentTimeMillis()
    }
}