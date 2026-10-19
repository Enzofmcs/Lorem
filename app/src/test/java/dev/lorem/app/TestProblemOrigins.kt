package dev.lorem.app

import dev.lorem.app.domain.model.CodeforcesProblem
import dev.lorem.app.domain.model.VerifiedProblemOrigin

/** Synthetic metadata, explicit only in tests; never infers dates from identities. */
fun CodeforcesProblem.withVerifiedTestOrigin(start: Long = VerifiedProblemOrigin.CUTOFF_EPOCH_MILLIS) = copy(
    origin = VerifiedProblemOrigin(id.contestId, start, 1_700_000_000_000L),
)
