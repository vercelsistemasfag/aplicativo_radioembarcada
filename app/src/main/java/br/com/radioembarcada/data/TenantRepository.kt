package br.com.radioembarcada.data

import br.com.radioembarcada.model.Tenant

// Uma futura fonte HTTP pode implementar este contrato sem alterar o player.
fun interface TenantConfigSource { fun load(): Tenant }

class TenantRepository(private val source: TenantConfigSource) {
    val activeTenant: Tenant by lazy { source.load() }
}
