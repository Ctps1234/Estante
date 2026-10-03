package com.example.estante.pdf

import kotlin.math.max
import kotlin.math.min

/**
 * Entrada do sumário de um livro.
 *
 * As coordenadas [xMin], [xMax], [yMin] e [yMax] são **normalizadas (0..1)**
 * em relação à página do sumário onde a entrada aparece — assim o teste de
 * toque funciona em qualquer resolução de tela.
 */
data class TocEntry(
    val title: String,
    /** Página destino (índice 0-based dentro do arquivo). */
    val targetIndex: Int,
    /** Página do sumário onde a entrada está (para zonas de toque); -1 = veio do outline do PDF. */
    val tocPageIndex: Int = -1,
    /** Caixa da linha na página do sumário, normalizada (0..1). */
    val xMin: Float = 0f,
    val xMax: Float = 1f,
    val yMin: Float = 0f,
    val yMax: Float = 1f
)

enum class TocSource { OUTLINE, TEXT }

/**
 * Sumário extraído de um PDF (ver [PdfTocParser]).
 *
 * @param entries     entradas para o painel "Sumário" (outline do PDF ou texto detectado)
 * @param tapEntries  subconjunto com caixa — clicável direto na página do sumário
 */
data class PdfToc(
    val entries: List<TocEntry>,
    val tapEntries: List<TocEntry> = emptyList(),
    val source: TocSource = TocSource.TEXT
) {
    /**
     * Entrada cuja linha contém o ponto (u, v) normalizado da página
     * [pageIndex], ou null. As áreas de toque são ampliadas (tolerância de
     * dedo) e se estendem até a borda direita da linha, onde ficam os
     * números de página.
     */
    fun entryAt(pageIndex: Int, u: Float, v: Float): TocEntry? {
        var best: TocEntry? = null
        for (entry in tapEntries) {
            if (entry.tocPageIndex != pageIndex) continue
            val pad = (entry.yMax - entry.yMin) * 0.4f + 0.008f
            val insideV = v >= entry.yMin - pad && v <= entry.yMax + pad
            val insideU = u >= (entry.xMin - 0.05f).coerceAtLeast(0f)
            if (insideV && insideU && (best == null || entry.yMin > best.yMin)) {
                best = entry
            }
        }
        return best
    }
}

/* ------------------------------------------------------------------ */
/* Lógica pura de análise do sumário (sem dependências de Android —   */
/* testável na JVM). Ver PdfTocLogicTest.                              */
/* ------------------------------------------------------------------ */

/** Palavra extraída da página (coordenadas em pontos PDF, origem no topo). */
internal data class TocWord(
    val text: String,
    val xMin: Float,
    val xMax: Float,
    /** Y da linha de base, a partir do topo da página. */
    val yBaseline: Float,
    /** Altura da fonte aproximada, em pontos. */
    val fontHeight: Float
)

/** Linha da página, com texto concatenado e caixa envolvente. */
internal data class TocLine(
    /** Palavras ordenadas da esquerda para a direita. */
    val words: List<TocWord>,
    val text: String,
    val xMin: Float,
    val xMax: Float,
    /** Caixa vertical (topo/fundo), já com folga para a altura da fonte. */
    val yTop: Float,
    val yBottom: Float,
    val fontHeight: Float
)

/** Entrada detectada numa linha de sumário, antes de resolver a página real. */
internal data class ParsedTocEntry(
    val title: String,
    /** Número de página como impresso no livro (1-based). */
    val printedPage: Int,
    val xMin: Float,
    val xMax: Float,
    val yTop: Float,
    val yBottom: Float
)

/** Quebra de linhas e espaços exóticos. */
private val WHITESPACE = Regex("[\\s\\u00A0\\u2007\\u202F]+")

/** Entrada de sumário: título + separadores + número no fim da linha. */
private val TRAILING_NUMBER = Regex("^(.+?)[\\s.·•…\\-–—]*([0-9]{1,4})$")

/** Pontos de guia (dot leaders), com ou sem espaços entre eles. */
private val LEADERS = Regex("(?:[.·•…]\\s*){3,}")

private fun isSeparator(c: Char): Boolean =
    c.isWhitespace() || c in ".·•…-–—"

/**
 * Agrupa palavras em linhas pela proximidade da linha de base.
 * (As palavras chegam na ordem de leitura do extrator de texto.)
 */
internal fun groupWordsIntoLines(words: List<TocWord>): List<TocLine> {
    if (words.isEmpty()) return emptyList()

    val sorted = words.sortedWith(compareBy({ it.yBaseline }, { it.xMin }))
    val lines = mutableListOf<MutableLine>()
    for (w in sorted) {
        val current = lines.lastOrNull()
        val fits = current != null &&
            kotlin.math.abs(w.yBaseline - current.yBaseline) <
            0.5f * max(max(w.fontHeight, current.fontHeight), 8f)
        if (fits && current != null) {
            current.xMin = min(current.xMin, w.xMin)
            current.xMax = max(current.xMax, w.xMax)
            current.yBaseline = max(current.yBaseline, w.yBaseline)
            current.fontHeight = max(current.fontHeight, w.fontHeight)
            current.words.add(w)
        } else {
            lines += MutableLine(
                xMin = w.xMin,
                xMax = w.xMax,
                yBaseline = w.yBaseline,
                fontHeight = w.fontHeight,
                words = mutableListOf(w)
            )
        }
    }
    return lines
        .map { m ->
            val text = m.words.joinToString(" ") { it.text }.replace(WHITESPACE, " ").trim()
            val h = max(m.fontHeight, 8f)
            TocLine(
                words = m.words.sortedBy { it.xMin },
                text = text,
                xMin = m.xMin,
                xMax = m.xMax,
                yTop = m.yBaseline - h * 0.85f,
                yBottom = m.yBaseline + h * 0.30f,
                fontHeight = h
            )
        }
        .sortedBy { it.yTop }
}

private class MutableLine(
    var xMin: Float,
    var xMax: Float,
    var yBaseline: Float,
    var fontHeight: Float,
    val words: MutableList<TocWord>
)

/**
 * Interpreta uma linha como possível entrada de sumário.
 *
 * Requisitos: título com pelo menos uma letra, número de página plausível ao
 * final e um "cheiro" de sumário — pontos de guia, número alinhado à direita
 * ou espaço grande entre o título e o número.
 */
internal fun parseTocEntry(line: TocLine, pageWidth: Float, maxPrintedPage: Int): ParsedTocEntry? {
    val text = line.text
    if (text.length < 3) return null
    if (text.matches(Regex("[0-9]{1,4}"))) return null // fólio de página

    val match = TRAILING_NUMBER.find(text) ?: return null
    val printed = match.groupValues[2].toIntOrNull() ?: return null
    if (printed < 1 || printed > maxPrintedPage) return null

    val title = match.groupValues[1].trim { isSeparator(it) }.trim()
    if (title.length < 2) return null
    if (!title.any { it.isLetter() }) return null

    // Cheiro de sumário (qualquer um serve):
    val hasLeaders = LEADERS.containsMatchIn(text)
    val lastWord = line.words.lastOrNull()
    val numberRight = lastWord != null && pageWidth > 0f && lastWord.xMin >= pageWidth * 0.55f
    val prevXMax = line.words.dropLast(1).maxOfOrNull { it.xMax } ?: 0f
    val hasGap = lastWord != null && (lastWord.xMin - prevXMax) > 1.2f * line.fontHeight
    if (!hasLeaders && !numberRight && !hasGap) return null

    return ParsedTocEntry(
        title = title,
        printedPage = printed,
        xMin = line.xMin,
        xMax = line.xMax,
        yTop = line.yTop,
        yBottom = line.yBottom
    )
}

/**
 * Extrai as entradas de sumário de uma página.
 *
 * Também junta entradas quebradas em duas linhas (título numa, pontos +
 * número na outra), desde que a segunda tenha pontos de guia.
 */
internal fun parseTocEntriesFromLines(
    lines: List<TocLine>,
    pageWidth: Float,
    maxPrintedPage: Int
): List<ParsedTocEntry> {
    val out = mutableListOf<ParsedTocEntry>()
    var pendingTitle: String? = null
    var pendingXMin = 0f
    var pendingXMax = 0f
    var pendingYTop = 0f
    var pendingYBottom = 0f

    for (line in lines) {
        val entry = parseTocEntry(line, pageWidth, maxPrintedPage)
        if (entry != null) {
            out += entry
            pendingTitle = null
            continue
        }

        val text = line.text
        val trailing = TRAILING_NUMBER.find(text)
        if (trailing != null && !text.any { it.isLetter() } && LEADERS.containsMatchIn(text)) {
            // Linha só com pontos de guia + número: título veio na linha anterior.
            val printed = trailing.groupValues[2].toIntOrNull()
            val title = pendingTitle
            if (printed != null && printed in 1..maxPrintedPage && title != null &&
                (line.yTop - pendingYBottom) < 2.0f * line.fontHeight
            ) {
                out += ParsedTocEntry(
                    title = title,
                    printedPage = printed,
                    xMin = min(pendingXMin, line.xMin),
                    xMax = max(pendingXMax, line.xMax),
                    yTop = min(pendingYTop, line.yTop),
                    yBottom = max(pendingYBottom, line.yBottom)
                )
            }
            pendingTitle = null
        } else if (text.any { it.isLetter() } && TRAILING_NUMBER.find(text) == null) {
            // Candidato a título de entrada quebrada em duas linhas.
            pendingTitle = text
            pendingXMin = line.xMin
            pendingXMax = line.xMax
            pendingYTop = line.yTop
            pendingYBottom = line.yBottom
        } else {
            pendingTitle = null
        }
    }
    return out
}

/** Número de página impresso sozinho na linha (fólio), se houver. */
internal fun folioOf(line: TocLine): Int? {
    val text = line.text
    if (!text.matches(Regex("[0-9]{1,4}"))) return null
    return text.toIntOrNull()
}

/**
 * Calcula o offset mais comum entre a numeração impressa e o índice real:
 * pares (número impresso, índice 0-based da página onde ele aparece).
 * Um alvo impresso T está no índice T + offset. Exige ≥ 2 concordâncias.
 */
internal fun computeFolioOffset(folios: List<Pair<Int, Int>>): Int? {
    val counts = HashMap<Int, Int>()
    for ((printed, index) in folios) {
        val offset = index - printed
        if (offset in -40..40) counts[offset] = (counts[offset] ?: 0) + 1
    }
    val best = counts.entries.maxByOrNull { it.value } ?: return null
    return if (best.value >= 2) best.key else null
}

/**
 * Converte o número impresso no sumário para o índice 0-based real.
 * Usa o mapa de fólios quando possível; senão, o offset (ou 1-based puro).
 * Retorna null se o alvo ficar fora do livro.
 */
internal fun resolveTocTarget(
    printed: Int,
    folioMap: Map<Int, Int>,
    offset: Int?,
    pageCount: Int
): Int? {
    folioMap[printed]?.let { return it }
    val index = if (offset == null) printed - 1 else printed + offset
    return if (index in 0 until pageCount) index else null
}
