package com.jrstudio.svyazsbogom.util

fun mayNeedUrgentHelp(text: String): Boolean {
    val t = text.lowercase()
    val markers = listOf(
        "хочу умереть",
        "хочу убить себя",
        "покончу с собой",
        "покончить с собой",
        "суицид",
        "не хочу жить",
        "себя убить"
    )
    return markers.any { t.contains(it) }
}
