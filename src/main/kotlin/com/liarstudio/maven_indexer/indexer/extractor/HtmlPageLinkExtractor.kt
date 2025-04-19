package com.liarstudio.maven_indexer.indexer.extractor

import com.liarstudio.maven_indexer.data.network.NetworkClient

class HtmlPageLinkExtractor(val networkClient: NetworkClient) {

    suspend fun invoke(url: String): List<String> {
        val htmlBody = networkClient.getBody(url)
        return regex.findAll(htmlBody)
            .map { it.groupValues[1] }
            .filter { it != "../" }
            .toList()
    }

    private companion object {
        val regex = Regex("""<a href="([^\"]+)\"""")
    }
}