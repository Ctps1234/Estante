package com.example.estante.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes da lógica pura de análise de sumário (sem Android/PDF — as linhas
 * simulam o que o extrator de texto da PdfBox devolve).
 */
class PdfTocLogicTest {

    private fun word(text: String, xMin: Float, xMax: Float, y: Float, fh: Float = 12f) =
        TocWord(text, xMin, xMax, y, fh)

    /* ---------------- grouping de linhas ---------------- */

    @Test
    fun agrupa_palavras_em_linhas_pela_baseline() {
        val lines = groupWordsIntoLines(
            listOf(
                word("CAPÍTULO", 40f, 120f, 100f), word("I", 125f, 132f, 100f),
                word("HISTORY", 40f, 100f, 124f), word("45", 500f, 520f, 124f),
                word("CAPÍTULO", 40f, 120f, 148f), word("II", 125f, 140f, 148f)
            )
        )
        assertEquals(3, lines.size)
        assertEquals("CAPÍTULO I", lines[0].text)
        assertEquals("HISTORY 45", lines[1].text)
        assertTrue(lines[0].yTop < lines[1].yTop)
        assertTrue(lines[1].yTop < lines[2].yTop)
    }

    @Test
    fun baseline_parecida_mas_nao_igual_continua_na_mesma_linha() {
        val lines = groupWordsIntoLines(
            listOf(
                word("A", 40f, 50f, 100f, fh = 14f),
                word("B", 60f, 70f, 105f, fh = 14f) // < 0.5*14 de diferença
            )
        )
        assertEquals(1, lines.size)
        assertEquals("A B", lines[0].text)
    }

    /* ---------------- detecção de entradas ---------------- */

    @Test
    fun detecta_entrada_com_pontos_de_guia() {
        val lines = groupWordsIntoLines(
            listOf(
                word("I.", 40f, 55f, 100f), word("THE", 60f, 90f, 100f),
                word("POOL", 95f, 130f, 100f),
                word("............", 140f, 480f, 100f), word("7", 500f, 510f, 100f)
            )
        )
        val entries = parseTocEntriesFromLines(lines, pageWidth = 550f, maxPrintedPage = 300)
        assertEquals(1, entries.size)
        assertEquals("I. THE POOL", entries[0].title)
        assertEquals(7, entries[0].printedPage)
    }

    @Test
    fun detecta_entrada_com_numero_alinhado_a_direita() {
        val lines = groupWordsIntoLines(
            listOf(
                word("CHAPTER", 40f, 130f, 100f), word("ONE", 135f, 175f, 100f),
                word("12", 520f, 540f, 100f)
            )
        )
        val entries = parseTocEntriesFromLines(lines, pageWidth = 550f, maxPrintedPage = 300)
        assertEquals(1, entries.size)
        assertEquals("CHAPTER ONE", entries[0].title)
        assertEquals(12, entries[0].printedPage)
    }

    @Test
    fun rejeita_linha_so_com_numero_folio() {
        val lines = groupWordsIntoLines(listOf(word("12", 260f, 290f, 700f)))
        assertTrue(parseTocEntriesFromLines(lines, 550f, 300).isEmpty())
    }

    @Test
    fun rejeita_ano_no_fim_da_linha() {
        val lines = groupWordsIntoLines(
            listOf(
                word("Published", 40f, 120f, 100f), word("in", 125f, 140f, 100f),
                word("1886", 145f, 180f, 100f)
            )
        )
        // 1886 > maxPrintedPage e sem cheiro de sumário
        assertTrue(parseTocEntriesFromLines(lines, 550f, 300).isEmpty())
    }

    @Test
    fun rejeita_numero_colado_no_titulo_sem_cheiro_de_sumario() {
        val lines = groupWordsIntoLines(
            listOf(
                word("Rua", 40f, 70f, 100f), word("7", 72f, 80f, 100f)
            )
        )
        assertTrue(parseTocEntriesFromLines(lines, 550f, 300).isEmpty())
    }

    @Test
    fun entrada_quebrada_em_duas_linhas() {
        val lines = groupWordsIntoLines(
            listOf(
                word("THE", 40f, 80f, 100f), word("LONG", 85f, 130f, 100f),
                word("TALE", 135f, 175f, 100f),
                word("...........", 40f, 400f, 124f), word("87", 500f, 515f, 124f)
            )
        )
        val entries = parseTocEntriesFromLines(lines, 550f, 300)
        assertEquals(1, entries.size)
        assertEquals("THE LONG TALE", entries[0].title)
        assertEquals(87, entries[0].printedPage)
        // caixa cobre as duas linhas
        assertTrue(entries[0].yTop < 105f)
        assertTrue(entries[0].yBottom > 124f)
    }

    @Test
    fun linha_de_folio_nao_vira_entrada_com_titulo_da_linha_anterior() {
        // "O FIM" (texto) seguido de fólio "12" (sem pontos de guia)
        val lines = groupWordsIntoLines(
            listOf(
                word("FIM", 40f, 70f, 100f),
                word("12", 260f, 290f, 700f)
            )
        )
        assertTrue(parseTocEntriesFromLines(lines, 550f, 300).isEmpty())
    }

    /* ---------------- fólios e offset ---------------- */

    @Test
    fun offset_mais_comum_dos_folios() {
        // páginas impressas 1,2,5 nas páginas reais 4,5,8 → offset 3
        assertEquals(3, computeFolioOffset(listOf(1 to 4, 2 to 5, 5 to 8)))
    }

    @Test
    fun offset_exige_duas_concordancias() {
        assertNull(computeFolioOffset(listOf(1 to 4)))
        assertEquals(3, computeFolioOffset(listOf(1 to 4, 9 to 12)))
    }

    @Test
    fun fólio_de_uma_linha() {
        assertEquals(12, folioOf(groupWordsIntoLines(listOf(word("12", 1f, 2f, 3f)))[0]))
        assertNull(folioOf(groupWordsIntoLines(listOf(word("12.", 1f, 2f, 3f)))[0]))
        assertNull(folioOf(groupWordsIntoLines(listOf(word("pág. 12", 1f, 2f, 3f)))[0]))
    }

    /* ---------------- resolução de alvos ---------------- */

    @Test
    fun resolve_alvo_pelo_mapa_de_folios() {
        assertEquals(4, resolveTocTarget(1, mapOf(1 to 4), 3, 10))
    }

    @Test
    fun resolve_alvo_pelo_offset() {
        // sem mapa: índice = impresso + offset
        assertEquals(8, resolveTocTarget(5, emptyMap(), 3, 10))
    }

    @Test
    fun resolve_alvo_sem_informacao_usa_numeracao_direta() {
        // sem fólios e sem offset: impresso 5 → índice 4
        assertEquals(4, resolveTocTarget(5, emptyMap(), null, 10))
    }

    @Test
    fun resolve_alvo_fora_do_livro_retorna_null() {
        assertNull(resolveTocTarget(9, emptyMap(), null, 10))
        assertNull(resolveTocTarget(99, emptyMap(), 3, 10))
    }

    /* ---------------- zonas de toque ---------------- */

    @Test
    fun entryAt_encontra_entrada_pelo_toque() {
        val entry = TocEntry("Cap. 1", 10, 0, 0.05f, 0.9f, 0.30f, 0.35f)
        val toc = PdfToc(listOf(entry), listOf(entry))

        assertEquals(entry, toc.entryAt(0, 0.5f, 0.32f))
        assertEquals(entry, toc.entryAt(0, 0.98f, 0.31f)) // número à direita
        assertEquals(entry, toc.entryAt(0, 0.5f, 0.285f)) // tolerância de dedo
        assertNull(toc.entryAt(1, 0.5f, 0.32f))           // outra página
        assertNull(toc.entryAt(0, 0.5f, 0.9f))            // fora da linha
    }
}
