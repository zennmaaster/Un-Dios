package com.castor.core.common.model

enum class MessageSource(val packageName: String) {
    WHATSAPP("com.whatsapp"),
    TEAMS("com.microsoft.teams");

    companion object {
        fun fromPackageName(pkg: String): MessageSource? = entries.find { it.packageName == pkg }
    }
}
