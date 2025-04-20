package com.liarstudio.maven_indexer.indexer.extractor

import com.liarstudio.maven_indexer.data.network.NetworkClient

class HtmlPageLinkExtractor(val networkClient: NetworkClient) {

    /**
     * Extracts all links from an HTML page.
     *
     * **Algorithm:** Downloads HTML body of a page, get all the `<a href="...">` tags from it,
     * and extract values from them via Regex.
     *
     * Additional logic: parent links ('../') are not considered as a valid links and filtered.
     */
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