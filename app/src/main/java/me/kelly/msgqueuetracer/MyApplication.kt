package me.kelly.msgqueuetracer

import android.app.Application

class MyApplication:Application() {

    companion object{
        lateinit var sApplication:Application
    }

    override fun onCreate() {
        super.onCreate()
        sApplication = this
    }

}