package br.com.radioembarcada

import android.app.Application
import br.com.radioembarcada.data.TenantRepository
import br.com.radioembarcada.data.music.LocalAssetMusicProvider
import br.com.radioembarcada.data.music.MusicProvider
import br.com.radioembarcada.programming.AutomaticProgramming
import br.com.radioembarcada.storage.LocalTenantStore

class RadioApplication : Application() {
    val musicProvider: MusicProvider by lazy {
        LocalAssetMusicProvider(assets)
    }
    val programming by lazy { AutomaticProgramming(musicProvider) }
    val tenants by lazy { TenantRepository(LocalTenantStore(this)) }
}
