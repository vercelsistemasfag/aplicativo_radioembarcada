package br.com.radioembarcada

import android.app.Application
import android.net.ConnectivityManager
import android.util.Log
import br.com.radioembarcada.data.TenantRepository
import br.com.radioembarcada.data.music.LocalAssetMusicProvider
import br.com.radioembarcada.data.music.MusicProvider
import br.com.radioembarcada.programming.AutomaticProgramming
import br.com.radioembarcada.network.catalog.RemoteMusicProvider
import br.com.radioembarcada.storage.FileMusicCatalog
import br.com.radioembarcada.storage.LocalTenantStore
import br.com.radioembarcada.storage.FileProgrammingConfiguration
import br.com.radioembarcada.network.programming.RemoteProgrammingProvider

class RadioApplication : Application() {
    val musicProvider: MusicProvider by lazy {
        if (BuildConfig.USE_LOCAL_MUSIC) LocalAssetMusicProvider(assets)
        else RemoteMusicProvider(FileMusicCatalog(filesDir.resolve("music_catalog.json")),
            connected = { getSystemService(ConnectivityManager::class.java).activeNetwork != null })
    }
    val programming by lazy {
        val diagnostic: (String) -> Unit = { if (BuildConfig.DEBUG) Log.d("RadioDiagnostics", it) }
        AutomaticProgramming(musicProvider, programmingProvider = if (BuildConfig.USE_LOCAL_MUSIC) null
            else RemoteProgrammingProvider(tenants.activeTenant.clientId,
                FileProgrammingConfiguration(filesDir.resolve("programming.json")),
                connected = { getSystemService(ConnectivityManager::class.java).activeNetwork != null },
                diagnostic = diagnostic), diagnostic = diagnostic)
    }
    val tenants by lazy { TenantRepository(LocalTenantStore(this)) }
}
