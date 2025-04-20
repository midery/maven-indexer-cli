package com.liarstudio.maven_indexer.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText

/**
 * Simple abstraction over [HttpClient] to run requests through the network.
 */
class NetworkClient {

    private val client = HttpClient(CIO) {
        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 3)
            exponentialDelay()
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 5_000
        }

        expectSuccess = false
    }

    /**
     * Gets text body from the [url].
     */
    suspend fun getBody(url: String) = client.get(url).bodyAsText()
}