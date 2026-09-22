package dev.lorem.app.data.remote

import dev.lorem.app.domain.model.CodeforcesProblem
import dev.lorem.app.domain.repository.CodeforcesRepository
import dev.lorem.app.domain.repository.CodeforcesUser
import dev.lorem.app.domain.repository.UserLookupResult
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

fun interface CodeforcesHttpClient {
    @Throws(IOException::class)
    suspend fun get(url: String): HttpResponse
}

data class HttpResponse(val statusCode: Int, val body: String)

class UrlConnectionCodeforcesHttpClient : CodeforcesHttpClient {
    override suspend fun get(url: String): HttpResponse = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.requestMethod = "GET"
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            HttpResponse(status, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } finally {
            connection.disconnect()
        }
    }
}

class CodeforcesApiRepository(
    private val gate: CodeforcesRequestGate,
    private val httpClient: CodeforcesHttpClient = UrlConnectionCodeforcesHttpClient(),
) : CodeforcesRepository {
    override suspend fun user(handle: String): UserLookupResult = try {
        gate.execute {
            val encoded = URLEncoder.encode(handle.trim(), Charsets.UTF_8.name())
            val response = httpClient.get("https://codeforces.com/api/user.info?handles=$encoded")
            when {
                response.statusCode == 429 -> UserLookupResult.RateLimited
                response.statusCode !in 200..299 -> UserLookupResult.NetworkFailure
                else -> parseUserResponse(response.body)
            }
        }
    } catch (_: IOException) {
        UserLookupResult.NetworkFailure
    }

    override suspend fun problems(): List<CodeforcesProblem> = emptyList()

    private fun parseUserResponse(body: String): UserLookupResult {
        val json = try {
            JSONObject(body)
        } catch (_: Exception) {
            return UserLookupResult.ApiFailure("Resposta inválida do Codeforces.")
        }
        if (json.optString("status") == "FAILED") {
            val comment = json.optString("comment", "Falha informada pelo Codeforces.")
            return when {
                comment.contains("not found", ignoreCase = true) -> UserLookupResult.UserNotFound
                comment.contains("limit", ignoreCase = true) -> UserLookupResult.RateLimited
                else -> UserLookupResult.ApiFailure(comment)
            }
        }
        if (json.optString("status") != "OK") {
            return UserLookupResult.ApiFailure("Resposta inválida do Codeforces.")
        }
        val user = json.optJSONArray("result")?.optJSONObject(0)
            ?: return UserLookupResult.ApiFailure("Usuário ausente na resposta do Codeforces.")
        val canonicalHandle = user.optString("handle").takeIf(String::isNotBlank)
            ?: return UserLookupResult.ApiFailure("Handle ausente na resposta do Codeforces.")
        val fullName = listOf(user.optString("firstName"), user.optString("lastName"))
            .filter(String::isNotBlank)
            .joinToString(" ")
            .ifBlank { canonicalHandle }
        return UserLookupResult.Success(
            CodeforcesUser(
                handle = canonicalHandle,
                displayName = fullName,
                rating = if (user.has("rating")) user.optInt("rating") else null,
            ),
        )
    }
}
