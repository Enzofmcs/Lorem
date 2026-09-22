package dev.lorem.app.domain

import dev.lorem.app.domain.model.CodeforcesSubmission
import dev.lorem.app.domain.model.ProblemHistory

fun buildProblemHistory(submissions: List<CodeforcesSubmission>): List<ProblemHistory> =
    submissions
        .groupBy(CodeforcesSubmission::problemId)
        .map { (problemId, problemSubmissions) ->
            ProblemHistory(
                problemId = problemId,
                attempted = true,
                hasAcceptedSubmission = problemSubmissions.any { it.verdict == "OK" },
            )
        }
