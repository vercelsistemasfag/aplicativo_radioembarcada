package br.com.radioembarcada.data.music

import br.com.radioembarcada.model.Track

interface MusicProvider {
    val providesCompleteCatalog: Boolean get() = false
    val supportsSavedCatalog: Boolean get() = false
    val catalogRevision: Long? get() = null
    val requiresNetwork: Boolean get() = true
    fun isAvailable(connected: Boolean): Boolean = !requiresNetwork || connected
    suspend fun fetchTracks(limit: Int, offset: Int): List<Track>
}

/** Mensagens públicas sanitizadas: nunca expor URL de requisição/client_id na UI. */
class MusicProviderException(val userMessage: String, val retryable: Boolean) :
    Exception(userMessage)
