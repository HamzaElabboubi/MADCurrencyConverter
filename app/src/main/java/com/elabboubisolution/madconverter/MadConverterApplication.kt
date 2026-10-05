package com.elabboubisolution.madconverter

import android.app.Application

class MadConverterApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
