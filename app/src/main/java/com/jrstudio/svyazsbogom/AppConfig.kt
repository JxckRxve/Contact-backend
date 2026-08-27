package com.jrstudio.svyazsbogom

object AppConfig {
    // Emulator -> backend on the same computer.
    const val BASE_URL = "https://contact-backend-u3ug.onrender.com/"

    // Replace these when YooKassa/payment links are approved.
    const val UNLOAD_PAYMENT_URL = "https://example.com/unload"
    const val SUPPORT_URL = "https://example.com/support"

    // Keep false until qualified specialists are actually providing the service.
    const val VERIFIED_PSYCHOLOGISTS = false
}
