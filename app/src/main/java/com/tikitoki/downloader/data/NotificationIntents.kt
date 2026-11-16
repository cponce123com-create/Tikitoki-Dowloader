package com.tikitoki.downloader.data

import android.content.Context
import android.content.Intent

/**
 * Resolve the application's launcher without coupling background work to an Activity class.
 * Keep the normal launch intent explicit and preserve the previous empty action and flags.
 */
internal fun notificationLaunchIntent(context: Context): Intent {
    val launcher = context.packageManager.getLaunchIntentForPackage(context.packageName)
    val component = launcher?.component
    return if (component != null) {
        Intent().setComponent(component)
    } else {
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName)
    }
}
