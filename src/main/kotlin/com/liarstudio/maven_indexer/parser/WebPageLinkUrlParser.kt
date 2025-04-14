package com.liarstudio.maven_indexer.parser

import com.liarstudio.maven_indexer.data.NetworkClient

class WebPageLinkUrlParser(val networkClient: NetworkClient) {

    suspend fun parse(url: String): List<String> {
        return try {
            val htmlBody = networkClient.getBody(url)
            regex.findAll(htmlBody)
                .map { it.groupValues[1] }
                .filter { it != "../" }
                .toList()
        } catch (e: Exception) {
            println("⚠️ Error loading $url: ${e.message}")
            emptyList()
        }
    }

    private companion object {
        val regex = Regex("""<a href="([^\"]+)\"""")
    }
}