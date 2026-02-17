package com.castor.core.common.model

enum class PrivacyTier(val description: String) {
    LOCAL("Never leaves device"),
    ANONYMIZED("Anonymized cloud processing"),
    CLOUD("Cloud processing allowed")
}
