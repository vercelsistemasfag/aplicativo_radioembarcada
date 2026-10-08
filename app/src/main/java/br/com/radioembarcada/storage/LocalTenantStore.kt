package br.com.radioembarcada.storage

import android.content.Context
import androidx.core.content.edit
import br.com.radioembarcada.data.TenantConfigSource
import br.com.radioembarcada.model.Tenant
import org.json.JSONObject

class LocalTenantStore(private val context: Context) : TenantConfigSource {
    override fun load(): Tenant {
        val json = JSONObject(context.assets.open("tenant.json").bufferedReader().use { it.readText() })
        return Tenant(
            json.getString("clientId"), json.getString("activationCode"),
            json.getString("companyName"), json.getString("radioName"),
            json.getString("primaryColor"),
        ).also {
            // A configuração visual permanece no asset; persiste apenas a seleção mockada.
            context.getSharedPreferences("tenant", Context.MODE_PRIVATE).edit {
                putString("activeClientId", it.clientId)
            }
        }
    }
}
