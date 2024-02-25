package com.example.androidcarhelper2

class Radio(name: String, private var iconName: String, private var type: String) {
    private var name = name
    private var id = name
    private var urls = ArrayList<RadioUrl>()
    private var decodedUrls = ArrayList<String>()

    fun addUrl(url: String?, description: String?) {
        getUrls().add(RadioUrl(url!!, description!!))
        decodedUrls.add("")
    }

    fun setDecodedUrl(url: String, index: Int){
        decodedUrls.set(index, url)
    }

    fun getRadioUrlByUrl(url: String): RadioUrl? {
        var radioUrl: RadioUrl? = null
        for (i in getUrls().indices) {
            if (getUrls()[i].url === url) {
                radioUrl = getUrls()[i]
                break
            }
        }
        return radioUrl
    }

    fun getFirstUrl(): RadioUrl {
        return getUrls()[0]
    }

    fun getName(): String {
        return name
    }

    fun setName(name: String) {
        this.name = name
    }

    fun getId(): String {
        return id
    }

    fun getIconName(): String {
        return iconName
    }

    fun getType(): String {
        return type
    }

    fun setIconName(iconName: String) {
        this.iconName = iconName
    }

    fun getUrls(): ArrayList<RadioUrl> {
        return urls
    }

    fun getDecodedUrls(): ArrayList<String>{
        return decodedUrls
    }

    fun setUrls(urls: ArrayList<RadioUrl>) {
        this.urls = urls
    }
}