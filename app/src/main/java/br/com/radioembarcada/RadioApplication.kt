package br.com.radioembarcada

import android.app.Application
import br.com.radioembarcada.data.TenantRepository
import br.com.radioembarcada.network.jamendo.JamendoMusicProvider
import br.com.radioembarcada.programming.AutomaticProgramming
import br.com.radioembarcada.storage.LocalTenantStore

class RadioApplication : Application() {
    val programming by lazy { AutomaticProgramming(JamendoMusicProvider(BuildConfig.JAMENDO_CLIENT_ID)) }
    val tenants by lazy { TenantRepository(LocalTenantStore(this)) }
}
