package br.com.radioembarcada.network.programming

import br.com.radioembarcada.data.programming.ProgrammingProvider
import br.com.radioembarcada.model.StationProgramming
import br.com.radioembarcada.storage.SavedProgrammingConfiguration
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class RemoteProgrammingProvider(
    private val stationId: String,
    private val saved: SavedProgrammingConfiguration,
    private val connected: () -> Boolean = { true },
    private val download: () -> String = ::downloadProgramming,
    private val now: () -> Long = System::currentTimeMillis,
    private val diagnostic: (String) -> Unit = {},
) : ProgrammingProvider {
    private val mutex = Mutex()
    private var configuration: StationProgramming? = null
    private var diskRead = false
    private var nextRefresh = 0L

    override suspend fun load(): StationProgramming? = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!diskRead) {
                diskRead = true
                try { configuration = saved.read()?.let(::parseForStation) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { diagnostic("Configuração salva inválida ou inacessível; mantendo músicas.") }
            }
            if (connected() && now() >= nextRefresh) {
                try {
                    val json = download()
                    val loaded = parseForStation(json)
                    configuration = loaded
                    nextRefresh = now() + RemoteProgrammingConfiguration.REFRESH_INTERVAL_MS
                    try { saved.write(json) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { diagnostic("Falha ao salvar programação; versão válida mantida em memória.") }
                    diagnostic("Programação carregada: estação=${loaded.stationId}; versão=${loaded.version}; uma peça por troca; padrão=${loaded.rules.insertionPattern.joinToString(" -> ")}; stationIds=${loaded.stationIds.size}; jingles=${loaded.jingles.size}")
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    nextRefresh = now() + RemoteProgrammingConfiguration.FAILED_REFRESH_INTERVAL_MS
                    diagnostic("Falha de carregamento da programação; " +
                        if (configuration != null) "usando última configuração válida." else "continuando somente com músicas.")
                }
            }
            configuration
        }
    }

    private fun parseForStation(json: String): StationProgramming = RemoteProgrammingParser.parse(json).also {
        require(it.stationId == stationId) { "Unexpected station" }
    }
}

private fun downloadProgramming(): String {
    val connection = URL(RemoteProgrammingConfiguration.URL).openConnection() as HttpsURLConnection
    try {
        connection.connectTimeout = RemoteProgrammingConfiguration.CONNECT_TIMEOUT_MS
        connection.readTimeout = RemoteProgrammingConfiguration.READ_TIMEOUT_MS
        connection.instanceFollowRedirects = false
        if (connection.responseCode != HttpsURLConnection.HTTP_OK) throw IOException("Programming unavailable")
        val bytes = connection.inputStream.use { source ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8_192)
            while (true) {
                val count = source.read(buffer)
                if (count < 0) break
                if (output.size() + count > RemoteProgrammingConfiguration.MAX_RESPONSE_BYTES) throw IOException("Programming too large")
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        return bytes.toString(Charsets.UTF_8)
    } finally { connection.disconnect() }
}
