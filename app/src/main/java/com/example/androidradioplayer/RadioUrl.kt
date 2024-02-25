package com.example.androidradioplayer

class RadioUrl(var url: String, var description: String) {
    var id = ""

    init {
        id = "radio_url_" + System.currentTimeMillis()
    }
}