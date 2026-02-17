package com.castor.feature.messaging.contacts

/**
 * Domain model representing a device contact entry.
 *
 * Maps to a row in ContactsContract.Contacts joined with
 * ContactsContract.CommonDataKinds.Phone. The [hasWhatsApp] and [hasTeams]
 * flags indicate whether we have observed messages from this contact
 * on the respective platform (cross-referenced via phone number normalization
 * against the MessageRepository).
 *
 * Rendered in the contacts list as a `/etc/passwd`-style entry:
 *   name:phone:photo:fav:wa:teams
 */
data class Contact(
    val id: Long,
    val name: String,
    val phoneNumber: String?,
    val photoUri: String?,
    val isFavorite: Boolean,
    val hasWhatsApp: Boolean = false,
    val hasTeams: Boolean = false
) {
    /** First character of the contact name, uppercased, for avatar fallback. */
    val initial: String
        get() = name.firstOrNull()?.uppercase() ?: "?"

    /**
     * Deterministic color index derived from the contact name hash.
     * Used to pick a consistent avatar background color per contact.
     */
    val colorIndex: Int
        get() = (name.hashCode() and 0x7FFFFFFF) % AVATAR_COLOR_COUNT

    companion object {
        /** Number of distinct avatar background colors in the palette. */
        const val AVATAR_COLOR_COUNT = 8
    }
}

/**
 * Sort modes available for the contact list.
 * Displayed as terminal-style flags in the UI:
 *   --sort=name-asc | --sort=name-desc | --sort=recent
 */
enum class ContactSortMode(val flag: String) {
    NAME_ASC("--sort=name-asc"),
    NAME_DESC("--sort=name-desc"),
    RECENT("--sort=recent")
}
