package net.notscape.browser.browser

/**
 * Settings search (beta.65 #12, the old S7 item): the
 * hub's search field matches rows by title or subtitle
 * and opens the page the row lives on. Pure matching so
 * it is unit-testable; the UI only renders the hits.
 */
data class SettingsSearchEntry(
    val title: String,
    val subtitle: String,
    /** The settings page key ("hub" never appears here —
     *  every entry opens a real page). */
    val page: String
)

/**
 * Every searchable settings row: the hub rows plus the
 * named rows that live one page down.
 */
val SETTINGS_SEARCH_ENTRIES = listOf(
    SettingsSearchEntry(
        "Appearance", "Theme, wallpaper, interface font", "appearance"
    ),
    SettingsSearchEntry(
        "Browsing", "Full screen, tab restore", "browsing"
    ),
    SettingsSearchEntry(
        "Search", "Search engine", "search"
    ),
    SettingsSearchEntry(
        "Privacy & blocking", "Ad and tracker blocking, cookies", "privacy"
    ),
    SettingsSearchEntry(
        "Video player", "Skip interval, subtitles, playback", "video"
    ),
    SettingsSearchEntry(
        "Data", "Backup, import, clear data", "data"
    ),
    SettingsSearchEntry(
        "Form fill", "Addresses and cards, save prompts", "formFill"
    ),
    SettingsSearchEntry(
        "Offer to save addresses", "Ask before saving a typed address",
        "formFill"
    ),
    SettingsSearchEntry(
        "Offer to save cards", "Ask before saving a typed card",
        "formFill"
    ),
    SettingsSearchEntry(
        "Manage addresses and cards", "Add, edit, or delete in the vault",
        "formFill"
    ),
    SettingsSearchEntry(
        "User scripts", "Your code, run on pages you choose", "scripts"
    ),
    SettingsSearchEntry(
        "Favorites", "Sites you starred, searchable", "favorites"
    ),
    SettingsSearchEntry(
        "Reading list", "Pages saved to read later", "readingList"
    ),
    SettingsSearchEntry(
        "Accessibility", "Reduced motion, text size", "accessibility"
    ),
    SettingsSearchEntry(
        "Text size", "Interface font size", "appearance"
    ),
    SettingsSearchEntry(
        "Clear browsing data", "History, cookies, cache, site data", "data"
    ),
    SettingsSearchEntry(
        "Delete browsing data on exit",
        "Clear data when you leave the app", "data"
    ),
    SettingsSearchEntry(
        "Export vault", "Vault export from the Data page", "data"
    ),
    SettingsSearchEntry(
        "Third-party cookies", "Block third-party cookies", "privacy"
    ),
    SettingsSearchEntry(
        "Blocked this session", "Blocked counts by category", "privacy"
    ),
    SettingsSearchEntry(
        "Force zoom", "Pages that disable scaling stay zoomable",
        "accessibility"
    ),
    SettingsSearchEntry(
        "System caption settings", "Android caption style", "accessibility"
    ),
    SettingsSearchEntry(
        "Reader", "Show the Read this article row", "accessibility"
    ),
    SettingsSearchEntry(
        "Reduced motion", "Still in-page animation", "accessibility"
    )
)

/**
 * Rows whose title or subtitle contains [query]
 * (case-insensitive). A blank query matches nothing —
 * the hub shows its rows instead.
 */
fun filterSettingsSearch(
    query: String,
    entries: List<SettingsSearchEntry> = SETTINGS_SEARCH_ENTRIES
): List<SettingsSearchEntry> {
    val q = query.trim()
    if (q.isBlank()) return emptyList()
    return entries.filter {
        it.title.contains(q, ignoreCase = true) ||
            it.subtitle.contains(q, ignoreCase = true)
    }
}
