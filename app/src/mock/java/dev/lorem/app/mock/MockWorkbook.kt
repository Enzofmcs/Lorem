package dev.lorem.app.mock

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale
import java.util.zip.ZipInputStream

internal const val MOCK_COUNT = 200
internal const val MOCK_HANDLE = "mock_excel"

internal enum class MockOutcome { AC, PENDENTE, UPSOLVE }

internal data class MockIpsum(
    val contestId: Long,
    val index: String,
    val name: String,
    val rating: Int,
    val tags: List<String>,
    val startedAt: Long,
    val duration: Long,
    val outcome: MockOutcome,
    val errors: Int,
    val hintAt: Long?,
    val upsolveAt: Long?,
)

internal data class MockWorkbook(val id: String, val ipsums: List<MockIpsum>)

internal class WorkbookException(message: String) : IllegalArgumentException(message)

private const val SHEET = "Ipsums"
private const val MAX_BYTES = 12 * 1024 * 1024
private const val DAY_MS = 86_400_000L
private val SHEET_PATH = Regex("xl/worksheets/[A-Za-z0-9_.-]+[.]xml")
private val COLUMN = Regex("([A-Z]{1,3})([1-9][0-9]{0,6})")
private val required = setOf("contest_id", "indice", "nome", "rating_problema", "tags",
    "inicio_utc", "duracao_min", "resultado", "erros")
private val columns = required + setOf("dica_min", "upsolve_utc")
private val officialTags = setOf(
    "*special", "2-sat", "binary search", "bitmasks", "brute force", "chinese remainder theorem",
    "combinatorics", "constructive algorithms", "data structures", "dfs and similar",
    "divide and conquer", "dp", "dsu", "expression parsing", "fft", "flows", "games", "geometry",
    "graph matchings", "graphs", "greedy", "hashing", "implementation", "interactive", "math",
    "matrices", "meet-in-the-middle", "number theory", "probabilities", "schedules",
    "shortest paths", "sortings", "string suffix structures", "strings", "ternary search",
    "trees", "two pointers",
)

internal fun readMockWorkbook(input: InputStream): MockWorkbook {
    val files = readZip(input)
    var relationship: String? = null
    var date1904 = false
    files.xml("xl/workbook.xml").scan {
        when (name) {
            "workbookPr" -> date1904 = attr("date1904") in setOf("1", "true")
            "sheet" -> if (attr("name") == SHEET) {
                relationship = (0 until attributeCount).firstOrNull { getAttributeName(it) == "id" }
                    ?.let(::getAttributeValue)
            }
        }
    }
    val sheetId = relationship ?: invalid("Crie uma aba chamada Ipsums.")
    var sheetPath: String? = null
    files.xml("xl/_rels/workbook.xml.rels").scan {
        if (name == "Relationship" && attr("Id") == sheetId && attr("TargetMode") != "External") {
            val target = attr("Target").orEmpty()
            sheetPath = if (target.startsWith("/")) target.drop(1) else "xl/$target"
        }
    }
    val path = sheetPath?.takeIf(SHEET_PATH::matches) ?: invalid("A aba Ipsums está inválida.")
    val shared = files["xl/sharedStrings.xml"]?.let(::xml)?.let { parser ->
        buildList { parser.scan { if (name == "si") add(readString()) } }
    }.orEmpty()

    val rawRows = linkedMapOf<Int, Map<String, Cell>>()
    files.xml(path).scan {
        if (name == "row") {
            val number = attr("r")?.toIntOrNull()?.takeIf { it >= 1 }
                ?: invalid("O Excel está sem a numeração das linhas.")
            val cells = readRow(number, shared)
            if (cells.isNotEmpty()) {
                if (rawRows.put(number, cells) != null) invalid("Linha $number repetida no arquivo.")
                if (rawRows.size > MOCK_COUNT + 1) invalid("Use exatamente $MOCK_COUNT linhas de dados.")
            }
        }
    }
    val header = rawRows.remove(1) ?: invalid("Coloque os cabeçalhos na linha 1.")
    val names = header.mapValues { (_, cell) -> cell.literal(1).lowercase(Locale.ROOT) }
    val missing = required - names.values.toSet()
    if (missing.isNotEmpty()) invalid("Faltam cabeçalhos na linha 1: ${missing.joinToString()}.")
    if (names.values.size != names.values.toSet().size) invalid("Há cabeçalhos repetidos na linha 1.")
    val extra = names.values.toSet() - columns
    if (extra.isNotEmpty()) invalid("Cabeçalhos desconhecidos na linha 1: ${extra.joinToString()}.")
    if (rawRows.size != MOCK_COUNT) invalid("Esperava $MOCK_COUNT linhas de dados. Encontrei ${rawRows.size}.")

    val problems = mutableMapOf<Pair<Long, String>, Int>()
    val rows = rawRows.map { (number, values) ->
        if (values.keys.any { it !in names }) invalid("Linha $number: há dados sem cabeçalho.")
        val data = values.mapKeys { (column, _) -> names.getValue(column) }
            .mapValues { (_, cell) -> cell.literal(number) }
        parseRow(data, number, date1904).also { ipsum ->
            val first = problems.put(ipsum.contestId to ipsum.index, number)
            if (first != null) invalid("Linha $number: problema já usado na linha $first.")
        }
    }.sortedWith(compareBy({ it.startedAt }, { it.contestId }, { it.index }))
    rows.zipWithNext().forEach { (before, after) ->
        if (before.startedAt + before.duration > after.startedAt) {
            invalid("As tentativas ${before.contestId}${before.index} e ${after.contestId}${after.index} se sobrepõem.")
        }
    }
    return MockWorkbook(datasetId(rows), rows)
}

private fun parseRow(data: Map<String, String>, line: Int, date1904: Boolean): MockIpsum {
    fun value(name: String) = data[name].orEmpty()
    fun checkField(valid: Boolean, name: String, explanation: String) {
        if (!valid) invalid("Linha $line, $name: $explanation")
    }
    fun integer(name: String, range: LongRange): Long {
        val number = value(name).toLongOrNull()
        checkField(number != null && number in range, name, "informe um inteiro de ${range.first} a ${range.last}.")
        return requireNotNull(number)
    }
    fun instant(name: String): Long {
        val value = value(name)
        val parsed = runCatching {
            val serial = value.toBigDecimalOrNull()
            if (serial != null) {
                require(serial >= BigDecimal.ONE && serial <= BigDecimal("74000") && serial.scale() <= 20)
                val epoch = if (date1904) LocalDate.of(1904, 1, 1) else LocalDate.of(1899, 12, 30)
                epoch.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() +
                    (serial * BigDecimal.valueOf(DAY_MS)).setScale(0, RoundingMode.HALF_UP).longValueExact()
            } else {
                val iso = value.replace(' ', 'T')
                runCatching { Instant.parse(iso) }.getOrElse {
                    LocalDateTime.parse(iso).toInstant(ZoneOffset.UTC)
                }.toEpochMilli()
            }
        }.getOrNull()
        checkField(parsed != null && parsed in 946_684_800_000L..4_102_444_800_000L,
            name, "informe uma data Excel ou ISO em UTC, de 2000 a 2100.")
        return requireNotNull(parsed)
    }
    fun minutes(name: String): Long {
        val parsed = runCatching {
            val input = value(name).replace(',', '.').toBigDecimal()
            require(input > BigDecimal.ZERO && input <= BigDecimal("120") && input.scale() <= 12)
            (input * BigDecimal.valueOf(60_000)).longValueExact()
        }.getOrNull()
        checkField(parsed != null && parsed in 1..7_200_000L, name, "use um valor maior que 0 e de até 120 minutos.")
        return requireNotNull(parsed)
    }

    val contestId = integer("contest_id", 1L..Int.MAX_VALUE.toLong())
    val index = value("indice").uppercase(Locale.ROOT)
    checkField(index.matches(Regex("[A-Z][A-Z0-9]{0,9}")), "indice", "use um índice como A ou B2.")
    val name = value("nome")
    checkField(name.length in 1..160, "nome", "use de 1 a 160 caracteres.")
    val rating = integer("rating_problema", 800L..3500L).toInt()
    checkField(rating % 100 == 0, "rating_problema", "use múltiplos de 100.")
    val tags = value("tags").split('|').map { it.trim().lowercase(Locale.ROOT) }.distinct().sorted()
    checkField(tags.size in 1..12 && tags.all { it in officialTags }, "tags", "use tags do Codeforces separadas por |.")
    val started = instant("inicio_utc")
    val duration = minutes("duracao_min")
    val outcome = MockOutcome.entries.firstOrNull { it.name == value("resultado").uppercase(Locale.ROOT) }
    checkField(outcome != null, "resultado", "use AC, PENDENTE ou UPSOLVE.")
    val errors = integer("erros", 0L..10_000).toInt()
    val hint = value("dica_min").takeIf(String::isNotEmpty)?.let { minutes("dica_min") }
    checkField(hint == null || hint < duration, "dica_min", "a dica precisa ocorrer antes do término.")
    val outside = value("upsolve_utc").takeIf(String::isNotEmpty)?.let { instant("upsolve_utc") }
    checkField(if (outcome == MockOutcome.UPSOLVE) outside != null && outside > started + duration else outside == null,
        "upsolve_utc", "preencha apenas em UPSOLVE, com uma data posterior ao encerramento.")
    return MockIpsum(contestId, index, name, rating, tags, started, duration, requireNotNull(outcome), errors,
        hint?.let { started + it }, outside)
}

private fun datasetId(rows: List<MockIpsum>): String {
    val hash = MessageDigest.getInstance("SHA-256")
    DataOutputStream(DigestOutputStream(ByteArrayOutputStream(), hash)).use { out ->
        out.writeUTF("lorem-mock-excel-v1")
        rows.forEach {
            out.writeLong(it.contestId)
            out.writeUTF(it.index)
            out.writeUTF(it.name)
            out.writeInt(it.rating)
            out.writeUTF(it.tags.joinToString("|"))
            out.writeLong(it.startedAt)
            out.writeLong(it.duration)
            out.writeUTF(it.outcome.name)
            out.writeInt(it.errors)
            out.writeLong(it.hintAt ?: 0L)
            out.writeLong(it.upsolveAt ?: 0L)
        }
    }
    return "mock-" + hash.digest().joinToString("") { "%02x".format(Locale.ROOT, it) }
}

private data class Cell(val value: String, val formula: Boolean, val error: Boolean) {
    fun literal(row: Int): String {
        if (formula || error) invalid("Linha $row: substitua fórmulas e erros por valores no Excel.")
        return value.trim()
    }
}

private fun XmlPullParser.readRow(row: Int, shared: List<String>): Map<String, Cell> = buildMap {
    val rowDepth = depth
    while (advance() != XmlPullParser.END_DOCUMENT) {
        if (eventType == XmlPullParser.END_TAG && depth == rowDepth) break
        if (eventType != XmlPullParser.START_TAG || name != "c") continue
        val ref = COLUMN.matchEntire(attr("r").orEmpty())
            ?.takeIf { it.groupValues[2].toInt() == row } ?: invalid("Linha $row: célula sem referência válida.")
        val cell = readCell(shared)
        if (cell.value.isNotBlank() || cell.formula || cell.error) {
            if (put(ref.groupValues[1], cell) != null) invalid("Linha $row: célula repetida.")
        }
    }
}

private fun XmlPullParser.readCell(shared: List<String>): Cell {
    val cellDepth = depth
    val type = attr("t")
    var value = ""
    var formula = false
    while (advance() != XmlPullParser.END_DOCUMENT) {
        if (eventType == XmlPullParser.END_TAG && depth == cellDepth) break
        if (eventType != XmlPullParser.START_TAG) continue
        when (name) {
            "v" -> value = nextText()
            "is" -> value = readString()
            "f" -> formula = true
        }
    }
    if (type == "s") {
        value = shared.getOrNull(value.toIntOrNull() ?: -1) ?: invalid("Texto compartilhado inválido no arquivo.")
    }
    return Cell(value, formula, type == "e")
}

private fun XmlPullParser.readString(): String = buildString {
    val startDepth = depth
    var inPhonetics = false
    while (advance() != XmlPullParser.END_DOCUMENT) {
        if (eventType == XmlPullParser.END_TAG && depth == startDepth) break
        if (eventType == XmlPullParser.END_TAG && name == "rPh") inPhonetics = false
        if (eventType == XmlPullParser.START_TAG && name == "rPh") inPhonetics = true
        if (eventType == XmlPullParser.START_TAG && name == "t" && !inPhonetics) append(nextText())
    }
}

private fun readZip(input: InputStream): Map<String, ByteArray> = buildMap {
    val buffer = ByteArray(8192)
    var total = 0
    var entries = 0
    ZipInputStream(input).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            if (++entries > 256) invalid("O arquivo Excel tem entradas demais.")
            val name = entry.name
            val keep = name == "xl/workbook.xml" || name == "xl/_rels/workbook.xml.rels" ||
                name == "xl/sharedStrings.xml" || SHEET_PATH.matches(name)
            val out = if (keep) ByteArrayOutputStream() else null
            while (true) {
                val read = zip.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_BYTES) invalid("A planilha descompactada deve ter até 12 MB.")
                out?.write(buffer, 0, read)
            }
            if (out != null && put(name, out.toByteArray()) != null) invalid("O arquivo Excel tem entradas repetidas.")
        }
    }
}

private fun Map<String, ByteArray>.xml(path: String) = xml(get(path) ?: invalid("Abra um arquivo Excel .xlsx válido."))
private fun xml(bytes: ByteArray): XmlPullParser = XmlPullParserFactory.newInstance().newPullParser().apply {
    setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
    setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
    setInput(ByteArrayInputStream(bytes), null)
}
private fun XmlPullParser.attr(name: String): String? = getAttributeValue(null, name)
private fun XmlPullParser.advance(): Int = nextToken().also {
    if (it == XmlPullParser.DOCDECL) invalid("Declarações XML externas não são aceitas na planilha.")
}
private inline fun XmlPullParser.scan(action: XmlPullParser.() -> Unit) {
    while (advance() != XmlPullParser.END_DOCUMENT) {
        if (eventType == XmlPullParser.START_TAG) action()
    }
}
private fun invalid(message: String): Nothing = throw WorkbookException(message)
