package dev.lorem.app.data.remote

import dev.lorem.app.domain.model.CodeforcesProblem
import dev.lorem.app.domain.model.CodeforcesSubmission
import dev.lorem.app.domain.model.ProblemId
import dev.lorem.app.domain.repository.CodeforcesRepository
import dev.lorem.app.domain.repository.CodeforcesUser
import dev.lorem.app.domain.repository.SubmissionHistoryResult
import dev.lorem.app.domain.repository.UserLookupResult
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
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
    private val submissionPageSize: Int = DEFAULT_SUBMISSION_PAGE_SIZE,
) : CodeforcesRepository {
    init {
        require(submissionPageSize > 0) { "submissionPageSize must be positive" }
    }

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

    override suspend fun submissionHistory(handle: String): SubmissionHistoryResult {
        val submissions = mutableListOf<CodeforcesSubmission>()
        val encodedHandle = URLEncoder.encode(handle.trim(), Charsets.UTF_8.name())
        var from = FIRST_SUBMISSION_INDEX

        return try {
            while (true) {
                val response = gate.execute {
                    httpClient.get(
                        "https://codeforces.com/api/user.status" +
                            "?handle=$encodedHandle&from=$from&count=$submissionPageSize",
                    )
                }
                when {
                    response.statusCode == 429 -> return SubmissionHistoryResult.RateLimited
                    response.statusCode !in 200..299 -> {
                        return SubmissionHistoryResult.HttpFailure(response.statusCode)
                    }
                }

                when (val page = parseSubmissionPage(response.body)) {
                    is SubmissionPageResult.Failed -> return SubmissionHistoryResult.Failed(page.message)
                    SubmissionPageResult.InvalidResponse -> {
                        return SubmissionHistoryResult.InvalidResponse
                    }
                    is SubmissionPageResult.Success -> {
                        submissions += page.submissions
                        if (page.receivedCount < submissionPageSize) {
                            return SubmissionHistoryResult.Success(submissions)
                        }
                    }
                }
                from += submissionPageSize
            }
            @Suppress("UNREACHABLE_CODE")
            SubmissionHistoryResult.Success(submissions)
        } catch (_: IOException) {
            SubmissionHistoryResult.NetworkFailure
        }
    }

    override suspend fun problems(): List<CodeforcesProblem> = emptyList()

    private fun parseSubmissionPage(body: String): SubmissionPageResult {
        val json = try {
            JSONObject(body)
        } catch (_: Exception) {
            return SubmissionPageResult.InvalidResponse
        }
        if (json.optString("status") == "FAILED") {
            return SubmissionPageResult.Failed(
                json.optString("comment", "Falha informada pelo Codeforces."),
            )
        }
        if (json.optString("status") != "OK") return SubmissionPageResult.InvalidResponse
        val result = json.optJSONArray("result") ?: return SubmissionPageResult.InvalidResponse
        return SubmissionPageResult.Success(
            submissions = result.validSubmissions(),
            receivedCount = result.length(),
        )
    }

    private fun JSONArray.validSubmissions(): List<CodeforcesSubmission> = buildList {
        for (position in 0 until length()) {
            val submission = optJSONObject(position) ?: continue
            val problem = submission.optJSONObject("problem") ?: continue
            val contestId = problem.optLong("contestId", -1L)
            val index = problem.optString("index").takeIf(String::isNotBlank) ?: continue
            val submissionId = submission.optLong("id", -1L)
            val creationTimeSeconds = submission.optLong("creationTimeSeconds", -1L)
            if (contestId <= 0 || submissionId <= 0 || creationTimeSeconds < 0) continue
            add(
                CodeforcesSubmission(
                    id = submissionId,
                    problemId = ProblemId(contestId, index),
                    verdict = submission.optString("verdict").takeIf(String::isNotBlank),
                    createdAt = Instant.ofEpochSecond(creationTimeSeconds),
                ),
            )
        }
    }

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

    private sealed interface SubmissionPageResult {
        data class Success(
            val submissions: List<CodeforcesSubmission>,
            val receivedCount: Int,
        ) : SubmissionPageResult

        data class Failed(val message: String) : SubmissionPageResult
        data object InvalidResponse : SubmissionPageResult
    }

    private companion object {
        const val FIRST_SUBMISSION_INDEX = 1
        const val DEFAULT_SUBMISSION_PAGE_SIZE = 10_000
    }
}
