package com.example.fajrbell

import android.app.Application

/** بيحتفظ بسياق التطبيق عشان حساب المواقيت يقرأ الموقع المحفوظ من أي مكان */
class FajrApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: FajrApp
            private set
    }
}
