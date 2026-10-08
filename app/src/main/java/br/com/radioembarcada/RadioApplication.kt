package br.com.radioembarcada

import android.app.Application
import br.com.radioembarcada.data.TenantRepository
import br.com.radioembarcada.data.music.LocalAssetMusicProvider
import br.com.radioembarcada.data.music.MusicProvider
import br.com.radioembarcada.network.jamendo.JamendoMusicProvider
import br.com.radioembarcada.programming.AutomaticProgramming
import br.com.radioembarcada.storage.LocalTenantStore

class RadioApplication : Application() {
    val musicProvider: MusicProvider by lazy {
        if (BuildConfig.DEBUG) LocalAssetMusicProvider(assets)
        else JamendoMusicProvider(BuildConfig.JAMENDO_CLIENT_ID)
    }
    val programming by lazy { AutomaticProgramming(musicProvider) }
    val tenants by lazy { TenantRepository(LocalTenantStore(this)) }
}
