package dev.lorem.app.data.cloud

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dev.lorem.app.domain.*
import dev.lorem.app.domain.cloud.*
import dev.lorem.app.domain.model.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TccDemoHistoryTest {
    @Test fun replayWorkbook() {
        val input = System.getenv("LOREM_DEMO_INPUT")
        assumeTrue("Use LOREM_DEMO_INPUT para reproduzir a amostra.", input != null)
        val sample = JSONObject(File(requireNotNull(input)).readText())
        val source = sample.getJSONObject("source")
        val checksum = source.getString("sha256")
        assertTrue(checksum.matches(Regex("[a-f0-9]{64}")))
        assertEquals(TCC_DEMO_ABILITY, source.getInt("ability"))
        val rows = sample.getJSONArray("attempts")
        assertEquals(TCC_DEMO_COUNT, rows.length())

        val model = ApplicationProvider.getApplicationContext<Application>().assets
            .open("virtual-duel-v1.json").bufferedReader().use { VirtualDuelCatalog(it.readText()) }
        val documents = JSONArray()
        val problems = mutableSetOf<ProblemId>()
        val finalized = mutableListOf<Ipsum>()
        var lorem = 800
        var dolor = INITIAL_DOLOR_MICROS
        var previousEnd = 0L
        val columns = "ipsum,lorem_antes_planilha,lorem_delta_planilha,lorem_após_planilha," +
            "lorem_antes_app,lorem_delta_app,lorem_após_app,dolor_antes_planilha,dolor_delta_planilha," +
            "dolor_após_planilha,dolor_antes_app,dolor_delta_app,dolor_após_app"
        val comparison = mutableListOf(columns)
        var loremDivergences = 0
        var dolorDivergences = 0
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            val started = Instant.parse(row.getString("startedAt")).toEpochMilli()
            val elapsed = row.getLong("elapsedMillis")
            assertTrue(started >= previousEnd && elapsed in 1..IPSUM_LIMIT_MILLIS)
            val end = started + elapsed
            val accepted = row.getBoolean("accepted")
            val errors = row.getInt("errors")
            assertTrue(errors >= 0)
            val id = ProblemId(row.getLong("contestId"), row.getString("index"))
            assertTrue(id != WATERMELON && problems.add(id))
            val difficulty = row.getInt("difficulty")
            val tags = row.getJSONArray("tags").let { values ->
                (0 until values.length()).map(values::getString).toSet()
            }
            val problem = CodeforcesProblem(id, row.getString("name"), difficulty, tags,
                VerifiedProblemOrigin(id.contestId, started, started, "tcc:workbook", "tcc-v1"))
            val category = IpsumCategory.valueOf(row.getString("category"))
            val recommendation = IpsumRecommendation(problem, category, lorem, difficulty, difficulty, 0, RecommendationFactors(emptyList(), 0))
            val duel = model.select(dolor, difficulty)
            val scoring = ScoringSnapshot(LEGACY_SCORING_POLICY, null, modelVersion = null, artifactSha256 = null,
                inputRating = null, unavailableReason = "simulated_sample")
            val reason = if (accepted) null else if (elapsed == IPSUM_LIMIT_MILLIS)
                CloudCloseReason.TIME_LIMIT_EXCEEDED else CloudCloseReason.GIVE_UP
            val failure = if (accepted) null else if (reason == CloudCloseReason.TIME_LIMIT_EXCEEDED)
                IpsumFailureReason.TIME_EXPIRED else IpsumFailureReason.LOGIC_NOT_FOUND
            val attempt = CloudIpsum("tcc-v1-$checksum-${i + 1}", cloudHandleKey(TCC_DEMO_HANDLE), i.toLong(),
                if (accepted) CloudIpsumState.ACTIVE else CloudIpsumState.CLOSED_AWAITING_VERIFICATION,
                Ipsum(id = i + 1L, ownerHandle = TCC_DEMO_HANDLE, problem = problem, initialLoremRating = lorem,
                    category = category, desiredRatingMin = difficulty, desiredRatingMax = difficulty,
                    selectedRating = difficulty, fallbackDistance = 0, startedAtEpochMillis = started,
                    status = IpsumStatus.ACTIVE, scoringSnapshot = scoring, virtualDuelSnapshot = duel),
                cutoffMillis = end.takeUnless { accepted }, closeReason = reason)
            val verification = if (accepted) CloudVerification.Accepted(end, errors) else CloudVerification.NoAccepted(errors)
            val final = finalizeCloudIpsum(attempt, verification, failure, model::evaluate)
            finalized += final
            val result = requireNotNull(final.virtualDuelResult)
            val sheetLorem = row.getJSONArray("sheetLorem")
            val sheetDolor = row.getJSONArray("sheetDolor")
            val appLorem = listOf(lorem, requireNotNull(final.ratingDelta), requireNotNull(final.finalLoremRating))
            val appDolor = listOf(result.beforeMicros, result.deltaMicros, result.afterMicros).map { it.toDouble() / DUEL_SCALE }
            if (appLorem.indices.any { appLorem[it] != sheetLorem.getInt(it) }) loremDivergences++
            if (appDolor.indices.any { abs(appDolor[it] - sheetDolor.getDouble(it)) >= 0.000001 }) dolorDivergences++
            comparison += (listOf(i + 1) + (0..2).map(sheetLorem::getInt) + appLorem +
                (0..2).map(sheetDolor::getDouble) + appDolor).joinToString(",")

            documents.put(JSONObject(mapOf(
                "id" to attempt.id, "problemKey" to "${id.contestId}_${id.index}", "version" to i,
                "snapshot" to snapshotOf(recommendation, lorem, TCC_DEMO_HANDLE, null, null, scoring, duel),
                "result" to resultOf(final).toMutableMap().apply { this["endedAt"] = Instant.ofEpochMilli(end).toString() },
                "startedAt" to Instant.ofEpochMilli(started).toString(), "closeReason" to reason?.name,
            )))
            lorem = appLorem.last()
            dolor = result.afterMicros
            previousEnd = end
        }
        val consolidated = calculateConsolidatedRating(finalized, ownerHandle = TCC_DEMO_HANDLE,
            estimatedRating = lorem).consolidatedRating
        val lastReference = rows.getJSONObject(rows.length() - 1)
        val report = JSONObject(mapOf(
            "source" to source, "attempts" to rows.length(), "accepted" to finalized.count { it.status == IpsumStatus.COMPLETED },
            "lorem" to mapOf("sheetFinal" to lastReference.getJSONArray("sheetLorem").getInt(2),
                "appFinal" to lorem, "rowsWithDifferences" to loremDivergences),
            "dolor" to mapOf("sheetFinal" to lastReference.getJSONArray("sheetDolor").getDouble(2),
                "appFinal" to dolor.toDouble() / DUEL_SCALE, "rowsWithDifferences" to dolorDivergences),
        ))
        val output = File("build/outputs/tcc-demo").apply { mkdirs() }
        output.resolve("comparison.csv").writeText(comparison.joinToString("\n", postfix = "\n"))
        output.resolve("report.json").writeText(report.toString(2) + "\n")
        output.resolve("baseline.json").writeText(JSONObject(mapOf(
            "dataset" to "tcc-v1-$checksum", "source" to source, "rating" to lorem, "dolorMicros" to dolor,
            "consolidated" to consolidated, "rows" to documents,
        )).toString() + "\n")
    }
}
