package com.tikitoki.downloader.data

import android.content.Context

/** No credentials, profile IDs, URLs or cookie values are stored in these preferences. */
internal enum class FacebookSessionOutcome {
    NOT_CHECKED,
    NO_AUTH_ERROR,
    LOGIN_REQUIRED,
}

internal data class FacebookSessionSummary(
    val cookies: FacebookCookieState,
    val outcome: FacebookSessionOutcome,
    val lastAttemptAt: Long,
)

/**
 * A local status history. A completed request without AuthRequired is not proof
 * that every Facebook album or private photo is accessible.
 */
internal object FacebookSessionHealth {
    private const val PREFS = "bajape_facebook_session_status"
    private const val OUTCOME = "last_outcome"
    private const val CHECKED_AT = "last_attempt_at"

    fun snapshot(context: Context): FacebookSessionSummary {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val outcome =
            when (prefs.getString(OUTCOME, null)) {
                "no_auth_error" -> FacebookSessionOutcome.NO_AUTH_ERROR
                "login_required" -> FacebookSessionOutcome.LOGIN_REQUIRED
                else -> FacebookSessionOutcome.NOT_CHECKED
            }
        return FacebookSessionSummary(
            PhotoSession.facebookCookieState(context),
            outcome,
            prefs.getLong(CHECKED_AT, 0L),
        )
    }

    fun recordLoginRequired(context: Context) = record(context, "login_required")

    fun recordNoAuthError(context: Context) = record(context, "no_auth_error")

    fun reset(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun record(context: Context, outcome: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(OUTCOME, outcome)
            .putLong(CHECKED_AT, System.currentTimeMillis())
            .apply()
    }
}
