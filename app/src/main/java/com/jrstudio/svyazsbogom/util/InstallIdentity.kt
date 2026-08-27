package com.jrstudio.svyazsbogom.util

import android.content.Context
import java.util.UUID

data class InstallIdentity(
    val conversationId: String,
    val installSecret: String
)

private const val PREFS = "divine_identity"

fun installIdentity(context: Context): InstallIdentity {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    var conversationId = prefs.getString("conversation_id", null)
    var installSecret = prefs.getString("install_secret", null)

    if (conversationId == null || installSecret == null) {
        conversationId = UUID.randomUUID().toString()
        installSecret = UUID.randomUUID().toString() + UUID.randomUUID().toString()
        prefs.edit()
            .putString("conversation_id", conversationId)
            .putString("install_secret", installSecret)
            .apply()
    }

    return InstallIdentity(conversationId, installSecret)
}

fun resetInstallIdentity(context: Context) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
}

fun onboardingAccepted(context: Context): Boolean =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getBoolean("onboarding_accepted", false)

fun setOnboardingAccepted(context: Context, value: Boolean) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit().putBoolean("onboarding_accepted", value).apply()
}
