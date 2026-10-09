package br.com.radioembarcada.data.news

import br.com.radioembarcada.model.NewsItem

fun interface NewsProvider {
    suspend fun latestUnplayed(excludedIds: Set<String>): NewsItem?
}
