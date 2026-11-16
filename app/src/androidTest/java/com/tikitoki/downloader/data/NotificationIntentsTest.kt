package com.tikitoki.downloader.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tikitoki.downloader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationIntentsTest {
    @Test
    fun notificationOpensTheApplicationEntryPoint() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = notificationLaunchIntent(context)

        assertEquals(context.packageName, intent.component?.packageName)
        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertNull(intent.action)
        assertEquals(0, intent.flags)
    }
}
