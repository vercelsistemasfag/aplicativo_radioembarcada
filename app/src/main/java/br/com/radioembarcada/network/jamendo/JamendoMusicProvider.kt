package br.com.radioembarcada.network.jamendo

import br.com.radioembarcada.data.music.MusicProvider
import br.com.radioembarcada.data.music.MusicProviderException
import br.com.radioembarcada.model.Track
import java.io.IOException
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

class JamendoMusicProvider(private val clientId: String) : MusicProvider {
    override suspend fun fetchTracks(limit: Int, offset: Int): List<Track> {
        if (clientId.isBlank()) throw MusicProviderException(
            "Fonte musical não configurada. Consulte as instruções de teste.", false)
        return coroutineScope {
            val responses = JamendoConfiguration.tagGroups.map { tags ->
                async {
                    try { Result.success(query(tags, limit, offset)) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: MusicProviderException) { Result.failure(error) }
                }
            }.awaitAll()
            val successful = responses.mapNotNull { it.getOrNull() }
            if (successful.isEmpty()) throw checkNotNull(responses.first().exceptionOrNull())
            successful.flatten().mapNotNull(JamendoTrackMapper::toTrack).distinctBy(Track::key)
        }
    }

    private suspend fun query(tags: String, limit: Int, offset: Int): List<JamendoTrackDto> =
        withContext(Dispatchers.IO) {
            val params = linkedMapOf(
                "client_id" to clientId, "format" to "json", "limit" to limit.coerceIn(1, 200).toString(),
                "offset" to offset.coerceAtLeast(0).toString(), "fuzzytags" to tags,
                "audioformat" to JamendoConfiguration.AUDIO_FORMAT, "speed" to JamendoConfiguration.SPEED,
                "durationbetween" to JamendoConfiguration.DURATION_BETWEEN,
                "include" to "licenses+musicinfo", "groupby" to "artist_id",
                "type" to "single+albumtrack", "order" to "relevance", "boost" to "popularity_month",
            )
            val query = encodeJamendoQuery(params)
            val connection = URL("${JamendoConfiguration.TRACKS_ENDPOINT}?$query")
                .openConnection() as HttpsURLConnection
            try {
                connection.connectTimeout = JamendoConfiguration.CONNECT_TIMEOUT_MS
                connection.readTimeout = JamendoConfiguration.READ_TIMEOUT_MS
                val status = connection.responseCode
                if (status != HttpsURLConnection.HTTP_OK) throw MusicProviderException(
                    "Catálogo temporariamente indisponível (HTTP $status).", status == 429 || status >= 500)
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                JamendoResponseParser.parse(response)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: MusicProviderException) {
                throw error
            } catch (_: IOException) {
                throw MusicProviderException("Não foi possível carregar o catálogo. Aguardando conexão.", true)
            } catch (_: org.json.JSONException) {
                throw MusicProviderException("O catálogo retornou uma resposta inválida.", false)
            } finally {
                connection.disconnect()
            }
        }
}

/** Os exemplos oficiais usam '+' no URL como separador (espaço após decode).
 * Preservar esse formato para listas; codificar '+' literal como %2B alteraria a consulta. */
internal fun encodeJamendoQuery(parameters: Map<String, String>): String =
    parameters.entries.joinToString("&") {
        val multiple = it.key in setOf("fuzzytags", "tags", "include", "speed", "type", "order")
        val value = if (multiple) it.value.replace('+', ' ') else it.value
        "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
    }
