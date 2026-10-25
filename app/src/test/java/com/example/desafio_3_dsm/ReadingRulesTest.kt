package com.example.desafio_3_dsm

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class ReadingRulesTest {
    @Test
    fun invalidFieldsExplainEachFailure() {
        val errors = ReadingRules.validate("A", "", "0", "-1", "Leyendo", 0)
        assertEquals(setOf("title", "author", "total", "read"), errors.keys)
        assertTrue(ReadingRules.validate("Libro", "Autor", "2147483648", "0", "Por leer", 0).containsKey("total"))
        assertTrue(ReadingRules.validate("Libro", "Autor", "10.5", "0", "Por leer", 0).containsKey("total"))
    }

    @Test
    fun finishedBooksRequireFullProgressAndRating() {
        assertEquals(setOf("read", "rating"), ReadingRules.validate("Libro", "Autor", "100", "50", "Terminado", 0).keys)
        assertTrue(ReadingRules.validate("Libro", "Autor", "100", "100", "Terminado", 5).isEmpty())
        assertTrue(ReadingRules.validate("Libro", "Autor", "100", "101", "Leyendo", 0).containsKey("read"))
        assertTrue(ReadingRules.validate("Libro", "Autor", "100", "1", "Por leer", 0).containsKey("read"))
    }

    @Test
    fun progressDoesNotOverflow() {
        assertEquals(100, Book(totalPages = Int.MAX_VALUE, pagesRead = Int.MAX_VALUE).progress)
        assertEquals(25, Book(totalPages = 200, pagesRead = 50).progress)
        assertEquals(0, Book().progress)
    }

    @Test
    fun annualStatisticsExcludeOtherYearsAndChooseEarlierTie() {
        val start = Calendar.getInstance().apply { clear(); set(2026, Calendar.JANUARY, 1) }.timeInMillis
        val first = Book(title = "Primero", status = "Terminado", rating = 5, finishedAt = start)
        val later = first.copy(title = "Después", finishedAt = start + 1000)
        val lastYear = first.copy(finishedAt = start - 1)
        val unfinished = first.copy(status = "Leyendo")
        val books = listOf(later, lastYear, unfinished, first)
        assertEquals(2, ReadingRules.finishedThisYear(books, 2026).size)
        assertEquals("Primero", ReadingRules.bestBook(books, 2026)?.title)
        assertNull(ReadingRules.bestBook(emptyList(), 2026))
    }

    @Test
    fun pageHistoryCountsOnlyNewProgressAndAppliesCorrections() {
        val first = ReadingRules.pageHistory(null, 100, 2025)
        val previous = Book(pagesRead = 100, pagesByYear = first)
        val updated = ReadingRules.pageHistory(previous, 160, 2026)
        assertEquals(mapOf("2025" to 100L, "2026" to 60L), updated)
        val same = ReadingRules.pageHistory(previous.copy(pagesRead = 160, pagesByYear = updated), 160, 2026)
        assertEquals(updated, same)
        val corrected = ReadingRules.pageHistory(previous.copy(pagesRead = 160, pagesByYear = updated), 80, 2026)
        assertEquals(mapOf("2025" to 80L), corrected)
    }
}
