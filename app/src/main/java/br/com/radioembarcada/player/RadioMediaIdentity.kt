package br.com.radioembarcada.player

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.MediaMetadata
import br.com.radioembarcada.R

/** Station presentation is stable while internal ProgramItems retain their original metadata. */
internal class RadioMediaIdentity(context: Context, stationName: String = context.getString(R.string.app_name)) {
    val title: String = context.getString(R.string.radio_live_title, stationName)
    // Media3 loads the packaged resource offline; no remote artwork or repeated bitmap parcels.
    val artworkUri: Uri = "android.resource://${context.packageName}/${R.drawable.radio_alce_media}".toUri()

    fun applyTo(builder: MediaMetadata.Builder): MediaMetadata.Builder = builder
        .setTitle(title).setDisplayTitle(title).setArtworkUri(artworkUri)
}
