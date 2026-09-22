package dev.lorem.app.domain.repository

import dev.lorem.app.domain.model.CodeforcesProblem

interface CodeforcesRepository {
    suspend fun problems(): List<CodeforcesProblem>
}
