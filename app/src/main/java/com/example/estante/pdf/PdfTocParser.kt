package com.example.estante.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * Extrai o sumário de um PDF. Duas fontes complementares:
 *
 *  1. **Outline** (marcadores) do próprio PDF, quando existe — é a fonte
 *     mais confiável para o painel "Sumário";
 *  2. **Texto** das páginas de sumário do livro ("CAPÍTULO I ..... 12"),
 *     com a numeração impressa convertida para a página real do arquivo
 *     (via fólios impressos no rodapé/topo das páginas).
 *
 * As entradas de texto também viram zonas de toque na própria página do
 * sumário — tocar na linha do capítulo pula direto para ela.
 *
 * Toda a extração é feita em uma passada e nunca lança exceção (retorna
 * null se não achar nada ou se o arquivo não puder ser lido).
 */
object PdfTocParser {

    /** Páginas iniciais vasculhadas atrás de uma página de sumário. */
    private const val MAX_TOC_SCAN = 30

    /** Limite de páginas amostradas para detectar fólios (numeração impressa). */
    private const val MAX_FOLIO_SAMPLE = 300

    fun parse(file: File): PdfToc? {
        return try {
            PDDocument.load(file).use { doc -> parseDocument(doc) }
        } catch (_: Exception) {
            null
        }
    }

    /* ---------------------------------------------------------------- */
    /* Outline do PDF                                                    */
    /* ---------------------------------------------------------------- */

    private fun parseOutline(doc: PDDocument, pageCount: Int): List<TocEntry>? {
        val root = runCatching { doc.documentCatalog.documentOutline }.getOrNull() ?: return null
        val entries = mutableListOf<TocEntry>()

        fun walk(item: PDOutlineItem, depth: Int) {
            if (entries.size >= 600 || depth > 6) return
            val title = runCatching { item.title?.trim().orEmpty() }.getOrDefault("")
            val page: PDPage? = runCatching { item.findDestinationPage(doc) }.getOrNull()
            if (title.isNotEmpty() && page != null) {
                val index = runCatching { doc.pages.indexOf(page) }.getOrDefault(-1)
                if (index in 0 until pageCount) {
                    entries += TocEntry(title = title, targetIndex = index)
                }
            }
            var child = item.firstChild
            var steps = 0
            while (child != null && steps++ < 600) {
                walk(child, depth + 1)
                child = child.nextSibling
            }
        }

        var child = root.firstChild
        var steps = 0
        while (child != null && steps++ < 600) {
            walk(child, 0)
            child = child.nextSibling
        }
        return if (entries.size >= 2) entries else null
    }

    /* ---------------------------------------------------------------- */
    /* Sumário em texto                                                  */
    /* ---------------------------------------------------------------- */

    /** Coletor de palavras com posição (chamado palavra a palavra). */
    private class WordCollector : PDFTextStripper() {
        val words = mutableListOf<TocWord>()

        override fun writeString(text: String, textPositions: List<TextPosition>) {
            if (text.isBlank() || textPositions.isEmpty()) return
            var xMin = Float.MAX_VALUE
            var xMax = -Float.MAX_VALUE
            var yBaseline = 0f
            var fontHeight = 0f
            for (tp in textPositions) {
                val x = tp.xDirAdj
                val width = tp.widthDirAdj
                if (x.isNaN() || width.isNaN()) continue
                xMin = min(xMin, x)
                xMax = max(xMax, x + width)
                yBaseline = tp.yDirAdj
                fontHeight = max(fontHeight, tp.fontSizeInPt)
            }
            val trimmed = text.trim()
            if (xMax < xMin || trimmed.isEmpty()) return
            words += TocWord(
                text = trimmed,
                xMin = xMin,
                xMax = xMax,
                yBaseline = yBaseline,
                fontHeight = max(fontHeight, 4f)
            )
        }
    }

    private fun extractLines(doc: PDDocument, pageIndex: Int): List<TocLine> {
        val stripper = WordCollector()
        stripper.sortByPosition = true
        stripper.startPage = pageIndex + 1 // o stripper usa páginas 1-based
        stripper.endPage = pageIndex + 1
        stripper.getText(doc)
        return groupWordsIntoLines(stripper.words)
    }

    private fun pageSizeOf(doc: PDDocument, index: Int): Pair<Float, Float>? {
        return try {
            val box = doc.getPage(index).cropBox
            val width = box.width
            val height = box.height
            if (width > 0f && height > 0f) width to height else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Retorna (entradas do painel, entradas clicáveis) ou null se não
     * encontrar páginas de sumário no início do livro.
     */
    private fun parseTextToc(doc: PDDocument, pageCount: Int): Pair<List<TocEntry>, List<TocEntry>>? {
        val scanLimit = min(MAX_TOC_SCAN, pageCount)

        // Páginas a extrair: as do início (sumário) + amostra esparsa (fólios).
        val sample = buildList {
            var p = scanLimit
            while (p < min(pageCount, MAX_FOLIO_SAMPLE)) {
                add(p)
                p += 12
            }
        }
        val linesByPage = HashMap<Int, List<TocLine>>()
        for (p in (0 until scanLimit).toList() + sample) {
            try {
                linesByPage[p] = extractLines(doc, p)
            } catch (_: Exception) {
                // página ilegível: ignora
            }
        }

        // 1) Páginas com cara de sumário (≥ 3 entradas).
        data class TocPage(
            val index: Int,
            val width: Float,
            val height: Float,
            val parsed: List<ParsedTocEntry>
        )
        val tocPages = mutableListOf<TocPage>()
        for (p in 0 until scanLimit) {
            val lines = linesByPage[p] ?: continue
            val size = pageSizeOf(doc, p) ?: continue
            val parsed = parseTocEntriesFromLines(lines, size.first, pageCount + 100)
            if (parsed.size >= 3) tocPages += TocPage(p, size.first, size.second, parsed)
        }
        if (tocPages.isEmpty()) return null

        // 2) Fólios: número impresso sozinho numa página → âncora para
        //    converter a numeração impressa em índice real.
        val folios = mutableListOf<Pair<Int, Int>>()
        val folioMap = HashMap<Int, Int>()
        for ((p, lines) in linesByPage) {
            if (p >= pageCount) continue
            val numbers = lines.mapNotNull { folioOf(it) }
            if (numbers.size == 1 && numbers[0] in 1..2000) {
                folios += numbers[0] to p
                folioMap.putIfAbsent(numbers[0], p)
            }
        }
        val offset = computeFolioOffset(folios)

        // 3) Resolve os alvos e normaliza as caixas para a página.
        val entries = mutableListOf<TocEntry>()
        for (tocPage in tocPages) {
            for (pe in tocPage.parsed) {
                val target = resolveTocTarget(pe.printedPage, folioMap, offset, pageCount)
                if (target == null || target == tocPage.index) continue
                entries += TocEntry(
                    title = pe.title,
                    targetIndex = target,
                    tocPageIndex = tocPage.index,
                    xMin = (pe.xMin / tocPage.width).coerceIn(0f, 1f),
                    xMax = (pe.xMax / tocPage.width).coerceIn(0f, 1f),
                    yMin = (pe.yTop / tocPage.height).coerceIn(0f, 1f),
                    yMax = (pe.yBottom / tocPage.height).coerceIn(0f, 1f)
                )
            }
        }
        if (entries.isEmpty()) return null
        return entries to entries
    }

    /* ---------------------------------------------------------------- */

    private fun parseDocument(doc: PDDocument): PdfToc? {
        val pageCount = doc.numberOfPages
        if (pageCount <= 1) return null

        val outline = parseOutline(doc, pageCount)
        val text = parseTextToc(doc, pageCount)

        val sheetEntries = outline ?: text?.first
        val tapEntries = text?.second ?: emptyList()
        if (sheetEntries.isNullOrEmpty() && tapEntries.isEmpty()) return null

        return PdfToc(
            entries = sheetEntries.orEmpty(),
            tapEntries = tapEntries,
            source = if (outline != null) TocSource.OUTLINE else TocSource.TEXT
        )
    }
}
