package com.castor.core.security

import com.castor.core.common.model.PrivacyTier
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Classifies user requests into privacy tiers and provides redaction/audit
 * capabilities. This is the gatekeeper that determines whether a query can
 * be processed locally, sent anonymized to the cloud, or sent as-is.
 *
 * Classification is keyword + pattern based. The classifier errs on the side
 * of caution: any ambiguous input defaults to LOCAL.
 */
@Singleton
class PrivacyClassifier @Inject constructor() {

    // =========================================================================
    // Patterns that indicate personal / on-device data (must stay LOCAL)
    // =========================================================================

    /** Keywords whose presence strongly indicates personal-data intent. */
    private val localKeywords: Set<String> = setOf(
        // Contacts & people
        "contact", "contacts", "phone number", "call", "dial",
        // Messaging
        "message", "messages", "sms", "text", "reply", "send message",
        "whatsapp", "telegram", "signal",
        // Calendar & reminders
        "calendar", "schedule", "meeting", "appointment", "reminder", "alarm",
        "event", "agenda",
        // Personal possessives / references
        "my", "mine", "private", "personal", "secret",
        // Files & media on device
        "photo", "gallery", "camera", "screenshot", "download", "file",
        "document", "pdf",
        // Device state
        "battery", "wifi", "bluetooth", "location", "gps",
        "notification", "notifications",
        // Sensitive data
        "password", "credential", "bank", "account", "ssn",
        "credit card", "health", "medical"
    )

    /** Regex patterns that match PII inline — phone numbers, emails, names
     *  after "from" / "to", etc. */
    private val localPatterns: List<Regex> = listOf(
        // US phone numbers: (xxx) xxx-xxxx, xxx-xxx-xxxx, +1xxxxxxxxxx
        Regex("""\+?1?[\s.-]?\(?\d{3}\)?[\s.-]?\d{3}[\s.-]?\d{4}"""),
        // International phone numbers (generic): +<country code> followed by digits
        Regex("""\+\d{1,4}[\s.-]?\d{4,14}"""),
        // Email addresses
        Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}"""),
        // Names after "from" or "to" (simple heuristic: capitalized words)
        Regex("""(?:from|to|for|about)\s+([A-Z][a-z]+(?:\s+[A-Z][a-z]+)*)"""),
        // Social security numbers: xxx-xx-xxxx
        Regex("""\b\d{3}-\d{2}-\d{4}\b"""),
        // Credit card numbers (basic: 4 groups of 4 digits)
        Regex("""\b\d{4}[\s-]?\d{4}[\s-]?\d{4}[\s-]?\d{4}\b"""),
        // Street addresses (number + street name pattern)
        Regex("""\b\d{1,5}\s+[A-Z][a-z]+(?:\s+[A-Z][a-z]+)*\s+(?:St|Ave|Blvd|Dr|Ln|Rd|Way|Ct|Pl)\b""")
    )

    // =========================================================================
    // Patterns that indicate cloud-suitable requests
    // =========================================================================

    /** Keywords that indicate a request explicitly wants external / real-time data. */
    private val cloudKeywords: Set<String> = setOf(
        "search the web", "google", "look up online", "latest news",
        "real-time", "realtime", "live", "current price",
        "stock price", "weather forecast", "trending",
        "use cloud", "use the cloud", "ask claude", "ask gpt",
        "ask openai", "ask anthropic", "external",
        "complex reasoning", "deep analysis",
        "translate to", "summarize this article",
        "browse", "web search", "internet"
    )

    // =========================================================================
    // Patterns that indicate anonymized-safe requests
    // =========================================================================

    /** Keywords for general-knowledge / factual queries safe for anonymized cloud. */
    private val anonymizedKeywords: Set<String> = setOf(
        "what is", "who is", "how to", "how do", "explain",
        "define", "definition", "meaning of",
        "code", "program", "function", "algorithm", "syntax",
        "python", "kotlin", "java", "javascript", "typescript",
        "bug", "error", "compile", "debug",
        "history of", "science", "math", "physics", "chemistry",
        "recipe", "instructions for", "tutorial",
        "compare", "difference between", "versus", "vs",
        "best practices", "recommendation",
        "capital of", "population of", "distance between"
    )

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Classify a user request into a [PrivacyTier].
     *
     * Priority order:
     *  1. If the input contains PII patterns or local keywords -> LOCAL
     *  2. If the input contains cloud-explicit keywords -> CLOUD
     *  3. If the input contains general-knowledge keywords -> ANONYMIZED
     *  4. Default -> LOCAL (safe fallback)
     */
    fun classify(input: String): PrivacyTier {
        val lower = input.lowercase().trim()

        // --- Step 1: Check for PII patterns first (highest priority) ---
        if (containsPersonalData(input)) {
            return PrivacyTier.LOCAL
        }

        // --- Step 2: Check for local-intent keywords ---
        if (localKeywords.any { keyword -> lower.contains(keyword) }) {
            return PrivacyTier.LOCAL
        }

        // --- Step 3: Check for explicit cloud intent ---
        if (cloudKeywords.any { keyword -> lower.contains(keyword) }) {
            return PrivacyTier.CLOUD
        }

        // --- Step 4: Check for general-knowledge (anonymizable) intent ---
        if (anonymizedKeywords.any { keyword -> lower.contains(keyword) }) {
            return PrivacyTier.ANONYMIZED
        }

        // --- Step 5: Default to LOCAL (privacy-first) ---
        return PrivacyTier.LOCAL
    }

    /**
     * Redact personally identifiable information from the input text.
     * Used when sending queries via the ANONYMIZED tier — strips PII
     * while preserving the core question structure.
     *
     * @param input The raw user input
     * @return The input with PII replaced by placeholder tokens
     */
    fun redact(input: String): String {
        var redacted = input

        // Redact email addresses
        redacted = Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}""")
            .replace(redacted, "[EMAIL]")

        // Redact SSNs
        redacted = Regex("""\b\d{3}-\d{2}-\d{4}\b""")
            .replace(redacted, "[SSN]")

        // Redact credit card numbers
        redacted = Regex("""\b\d{4}[\s-]?\d{4}[\s-]?\d{4}[\s-]?\d{4}\b""")
            .replace(redacted, "[CARD]")

        // Redact phone numbers (US patterns)
        redacted = Regex("""\+?1?[\s.-]?\(?\d{3}\)?[\s.-]?\d{3}[\s.-]?\d{4}""")
            .replace(redacted, "[PHONE]")

        // Redact international phone numbers
        redacted = Regex("""\+\d{1,4}[\s.-]?\d{4,14}""")
            .replace(redacted, "[PHONE]")

        // Redact names after "from", "to", "for", "about" (capitalized words)
        redacted = Regex("""(?i)(from|to|for|about)\s+([A-Z][a-z]+(?:\s+[A-Z][a-z]+)+)""")
            .replace(redacted) { match ->
                "${match.groupValues[1]} [NAME]"
            }

        // Redact street addresses
        redacted = Regex("""\b\d{1,5}\s+[A-Z][a-z]+(?:\s+[A-Z][a-z]+)*\s+(?:St|Ave|Blvd|Dr|Ln|Rd|Way|Ct|Pl)\b""")
            .replace(redacted, "[ADDRESS]")

        return redacted
    }

    /**
     * Audit a response from a cloud provider to check whether it contains
     * personal information that should not have been included.
     *
     * This acts as a safety net: if the cloud leaks PII back, we can
     * flag or suppress the response before showing it to the user.
     *
     * @param response The cloud provider's response text
     * @return `true` if the response appears clean; `false` if PII was detected
     */
    fun auditResponse(response: String): Boolean {
        // The response should NOT contain obvious PII patterns
        return !containsPersonalData(response)
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    /**
     * Returns `true` if the text contains any PII pattern matches.
     */
    private fun containsPersonalData(text: String): Boolean {
        return localPatterns.any { pattern -> pattern.containsMatchIn(text) }
    }

    companion object {
        /** Placeholder tokens used during redaction */
        val REDACTION_TOKENS = setOf("[EMAIL]", "[PHONE]", "[SSN]", "[CARD]", "[NAME]", "[ADDRESS]")
    }
}
