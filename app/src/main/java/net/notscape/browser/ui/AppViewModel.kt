package net.notscape.browser.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import net.notscape.browser.data.AppStore

/**
 * The activity-scoped owner of the AppStore (Phase F ViewModel
 * step). The store's lifetime is the ViewModel's rather than the
 * activity's, and screens keep receiving the same AppStore they
 * always have — this class is the anchor the rest of the
 * restructure hangs off, not a new API for the UI.
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    val store: AppStore = AppStore.get(app)
}
