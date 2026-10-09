package br.com.radioembarcada

import android.app.Application
import android.net.ConnectivityManager
import br.com.radioembarcada.data.TenantRepository
import br.com.radioembarcada.data.music.LocalAssetMusicProvider
import br.com.radioembarcada.data.music.MusicProvider
import br.com.radioembarcada.programming.AutomaticProgramming
import br.com.radioembarcada.network.catalog.RemoteMusicProvider
import br.com.radioembarcada.storage.FileMusicCatalog
import br.com.radioembarcada.storage.LocalTenantStore

class RadioApplication : Application() {
    val musicProvider: MusicProvider by lazy {
        if (BuildConfig.USE_LOCAL_MUSIC) LocalAssetMusicProvider(assets)
        else RemoteMusicProvider(FileMusicCatalog(filesDir.resolve("music_catalog.json")),
            connected = { getSystemService(ConnectivityManager::class.java).activeNetwork != null })
    }
    val programming by lazy { AutomaticProgramming(musicProvider) }
    val tenants by lazy { TenantRepository(LocalTenantStore(this)) }
}
