package com.castor.feature.messaging.contacts

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * ViewModel for the Contacts Hub screen.
 *
 * Reads device contacts from [ContactsContract] via the system [android.content.ContentResolver]
 * and exposes them as reactive [StateFlow]s for the Compose UI layer.
 *
 * Contact loading runs on [Dispatchers.IO] to avoid blocking the main thread.
 * The ViewModel provides intent-based actions for calling, messaging via WhatsApp/Teams,
 * and viewing contact details in the system contacts app.
 *
 * Data flow:
 *   ContentResolver → loadContacts() → _allContacts → combine(searchQuery, sortMode)
 *     → filteredContacts (exposed to UI)
 *     → favorites (filtered where isFavorite == true)
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val application: Application
) : ViewModel() {

    // ── Internal state ──────────────────────────────────────────────────────

    /** Raw contact list loaded from the device ContentResolver. */
    private val _allContacts = MutableStateFlow<List<Contact>>(emptyList())

    /** Whether contacts are currently being loaded from the system. */
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** Error message if contact loading fails (e.g., permission denied). */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** User's current search query — drives grep-style filtering. */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** Current sort mode for the contact list. */
    private val _sortMode = MutableStateFlow(ContactSortMode.NAME_ASC)
    val sortMode: StateFlow<ContactSortMode> = _sortMode.asStateFlow()

    /** Whether the contact permission has been granted. */
    private val _hasPermission = MutableStateFlow(false)
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    // ── Derived state ───────────────────────────────────────────────────────

    /**
     * Filtered and sorted contacts based on current search query and sort mode.
     * This is the primary data source for the "# all-contacts" section.
     *
     * Filtering: case-insensitive match against name or phone number.
     * Sorting: alphabetical (asc/desc) or by contact ID as a proxy for recency.
     */
    val filteredContacts: StateFlow<List<Contact>> = combine(
        _allContacts,
        _searchQuery,
        _sortMode
    ) { contacts, query, sort ->
        contacts
            .let { list ->
                if (query.isBlank()) list
                else {
                    val q = query.lowercase()
                    list.filter { contact ->
                        contact.name.lowercase().contains(q) ||
                            (contact.phoneNumber?.contains(q) == true)
                    }
                }
            }
            .let { list ->
                when (sort) {
                    ContactSortMode.NAME_ASC -> list.sortedBy { it.name.lowercase() }
                    ContactSortMode.NAME_DESC -> list.sortedByDescending { it.name.lowercase() }
                    ContactSortMode.RECENT -> list.sortedByDescending { it.id }
                }
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Favorite contacts, filtered from the full list.
     * Displayed in the horizontal scrolling "# favorites" row.
     */
    val favorites: StateFlow<List<Contact>> = combine(
        _allContacts,
        _searchQuery
    ) { contacts, query ->
        contacts
            .filter { it.isFavorite }
            .let { list ->
                if (query.isBlank()) list
                else {
                    val q = query.lowercase()
                    list.filter { contact ->
                        contact.name.lowercase().contains(q) ||
                            (contact.phoneNumber?.contains(q) == true)
                    }
                }
            }
            .sortedBy { it.name.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Total contact count for the status line. */
    val contactCount: StateFlow<Int> = combine(
        _allContacts,
        _searchQuery
    ) { contacts, _ ->
        contacts.size
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    // ── Actions ─────────────────────────────────────────────────────────────

    /**
     * Load contacts from the device ContentResolver.
     * Reads ContactsContract.Contacts joined with CommonDataKinds.Phone.
     * Must be called after READ_CONTACTS permission is granted.
     */
    fun loadContacts() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val contacts = withContext(Dispatchers.IO) {
                    readContactsFromDevice()
                }
                _allContacts.value = contacts
                _hasPermission.value = true
            } catch (e: SecurityException) {
                _error.value = "Permission denied: READ_CONTACTS required"
                _hasPermission.value = false
            } catch (e: Exception) {
                _error.value = "Failed to load contacts: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** Update the search query for grep-style filtering. */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /** Cycle through sort modes. */
    fun setSortMode(mode: ContactSortMode) {
        _sortMode.value = mode
    }

    /** Mark permission as granted and trigger a contact load. */
    fun onPermissionGranted() {
        _hasPermission.value = true
        loadContacts()
    }

    /** Mark permission as denied. */
    fun onPermissionDenied() {
        _hasPermission.value = false
        _error.value = "Permission denied: READ_CONTACTS required"
    }

    // ── Intent Builders ─────────────────────────────────────────────────────

    /**
     * Build an ACTION_DIAL intent for the given contact.
     * Uses ACTION_DIAL (not ACTION_CALL) so no CALL_PHONE permission is needed.
     */
    fun buildDialIntent(contact: Contact): Intent? {
        val number = contact.phoneNumber ?: return null
        return Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:${Uri.encode(number)}")
        }
    }

    /**
     * Build an intent to open a WhatsApp conversation with the contact.
     * Uses the wa.me deep link format which resolves to the WhatsApp app.
     * Phone number is stripped of non-digit characters for the URL.
     */
    fun buildWhatsAppIntent(contact: Contact): Intent? {
        val number = contact.phoneNumber ?: return null
        val cleanNumber = number.replace(Regex("[^\\d+]"), "")
            .removePrefix("+")
        return Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://wa.me/$cleanNumber")
            setPackage("com.whatsapp")
        }
    }

    /**
     * Build an intent to open a Microsoft Teams chat with the contact.
     * Uses the Teams deep link format for initiating a chat.
     */
    fun buildTeamsIntent(contact: Contact): Intent? {
        val number = contact.phoneNumber ?: return null
        return Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(
                "https://teams.microsoft.com/l/chat/0/0?users=${Uri.encode(number)}"
            )
            setPackage("com.microsoft.teams")
        }
    }

    /**
     * Build an intent to view the contact's details in the system Contacts app.
     * Opens the native contact detail view using the contact's content URI.
     */
    fun buildContactInfoIntent(contact: Contact): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            data = Uri.withAppendedPath(
                ContactsContract.Contacts.CONTENT_URI,
                contact.id.toString()
            )
        }
    }

    /**
     * Build an intent to create a new contact in the system Contacts app.
     * Opens the native "Add Contact" screen.
     */
    fun buildAddContactIntent(): Intent {
        return Intent(Intent.ACTION_INSERT).apply {
            type = ContactsContract.Contacts.CONTENT_TYPE
        }
    }

    // ── Private Helpers ─────────────────────────────────────────────────────

    /**
     * Read all contacts with phone numbers from the device ContentResolver.
     *
     * Query strategy:
     * 1. Query ContactsContract.CommonDataKinds.Phone for contacts with phone numbers
     * 2. Group by contact ID to deduplicate (a contact may have multiple phone numbers)
     * 3. Take the first phone number for each contact
     * 4. Read the starred/favorite flag from the Contacts table
     *
     * This runs on Dispatchers.IO — never call from the main thread.
     */
    private fun readContactsFromDevice(): List<Contact> {
        val contacts = mutableMapOf<Long, Contact>()
        val resolver = application.contentResolver

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
            ContactsContract.CommonDataKinds.Phone.STARRED
        )

        val sortOrder = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"

        resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID
            )
            val nameIndex = cursor.getColumnIndexOrThrow(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            )
            val numberIndex = cursor.getColumnIndexOrThrow(
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            val photoIndex = cursor.getColumnIndexOrThrow(
                ContactsContract.CommonDataKinds.Phone.PHOTO_URI
            )
            val starredIndex = cursor.getColumnIndexOrThrow(
                ContactsContract.CommonDataKinds.Phone.STARRED
            )

            while (cursor.moveToNext()) {
                val contactId = cursor.getLong(idIndex)
                // Skip duplicates — keep the first phone number per contact
                if (contacts.containsKey(contactId)) continue

                val name = cursor.getString(nameIndex) ?: continue
                val number = cursor.getString(numberIndex)
                val photoUri = cursor.getString(photoIndex)
                val isFavorite = cursor.getInt(starredIndex) == 1

                contacts[contactId] = Contact(
                    id = contactId,
                    name = name,
                    phoneNumber = number,
                    photoUri = photoUri,
                    isFavorite = isFavorite
                )
            }
        }

        return contacts.values.toList()
    }
}
