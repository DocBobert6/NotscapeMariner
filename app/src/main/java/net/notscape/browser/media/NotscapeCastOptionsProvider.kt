package net.notscape.browser.media

import android.content.Context
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.CastMediaControlIntent

/** Audit P-m25: Cast options. The default media receiver
 *  plays remote http(s) media with no custom receiver app. */
class NotscapeCastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions =
        CastOptions.Builder()
            .setReceiverApplicationId(
                CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID
            )
            .build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? =
        null
}
