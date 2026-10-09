package br.com.radioembarcada.network.news

import java.net.URI
import java.util.Locale

internal object OfficialNewsUrls {
    fun valid(url: String): Boolean = runCatching {
        val uri = URI(url)
        val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
        uri.scheme == "https" && (host == "ebc.com.br" || host.endsWith(".ebc.com.br")) &&
            uri.userInfo == null && uri.fragment == null && (uri.port == -1 || uri.port == 443)
    }.getOrDefault(false)

    fun audio(url: String): Boolean = valid(url) && runCatching {
        URI(url).path.lowercase(Locale.ROOT).endsWith(".mp3")
    }.getOrDefault(false)

    fun resolve(base: String, value: String): String? = runCatching {
        URI(base).resolve(value.trim()).toString().takeIf(::audio)
    }.getOrNull()
}
