package com.liarstudio.maven_indexer.data.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText

class NetworkClient {

    private val client = HttpClient(CIO) {
        install(ContentNegotiation.Plugin)
        install(HttpRequestRetry.Plugin)
        expectSuccess = false
    }

    suspend fun getBody(url: String) = client.get(url).bodyAsText()
}