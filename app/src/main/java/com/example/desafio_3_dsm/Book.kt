package com.example.desafio_3_dsm

import com.google.firebase.database.Exclude
import java.util.Calendar

data class Book(
    var title: String = "",
    var author: String = "",
    var genre: String = GENRES.first(),
    var totalPages: Int = 0,
    var pagesRead: Int = 0,
    var status: String = "Por leer",
    var rating: Int = 0,
    var finishedAt: Long = 0,
    var pagesByYear: Map<String, Long> = emptyMap(),
    @get:Exclude var id: String = ""
) {
    @get:Exclude
    val progress: Int get() = if (totalPages > 0) (pagesRead.toLong() * 100 / totalPages).toInt() else 0

    companion object {
        val GENRES = listOf("Novela", "Ciencia ficción", "Historia", "Desarrollo personal")
        val STATUSES = listOf("Por leer", "Leyendo", "Terminado")
    }
}

object ReadingRules {
    fun validate(title: String, author: String, total: String, read: String, status: String, rating: Int): Map<String, String> {
        val errors = linkedMapOf<String, String>()
        if (title.trim().length !in 2..60) errors["title"] = "El título debe tener entre 2 y 60 caracteres."
        if (author.trim().length !in 2..60) errors["author"] = "El autor debe tener entre 2 y 60 caracteres."
        val pages = total.toIntOrNull()
        val completed = read.toIntOrNull()
        if (pages == null || pages <= 0) errors["total"] = "El total de páginas debe ser un entero mayor que cero."
        if (completed == null || completed < 0 || (pages != null && completed > pages)) {
            errors["read"] = "Las páginas leídas deben ser un entero entre 0 y el total de páginas."
        }
        if (status == "Por leer" && completed != null && completed != 0) errors["read"] = "Un libro por leer debe tener 0 páginas leídas."
        if (status == "Terminado" && pages != null && completed != pages) errors["read"] = "Un libro terminado debe tener todas las páginas leídas."
        if (status == "Terminado" && rating !in 1..5) errors["rating"] = "Selecciona una calificación de 1 a 5 estrellas."
        return errors
    }

    fun year(timestamp: Long = System.currentTimeMillis()): Int = Calendar.getInstance().apply { timeInMillis = timestamp }.get(Calendar.YEAR)

    fun finishedThisYear(books: List<Book>, year: Int): List<Book> = books.filter {
        it.status == "Terminado" && it.finishedAt > 0 && year(it.finishedAt) == year
    }

    fun bestBook(books: List<Book>, year: Int): Book? = finishedThisYear(books, year)
        .sortedWith(compareByDescending<Book> { it.rating }.thenBy { it.finishedAt }.thenBy { it.id }).firstOrNull()

    fun pageHistory(previous: Book?, pages: Int, year: Int): Map<String, Long> {
        val history = previous?.pagesByYear?.toMutableMap() ?: mutableMapOf()
        var delta = pages.toLong() - (previous?.pagesRead ?: 0)
        val key = year.toString()
        if (delta >= 0) {
            history[key] = (history[key] ?: 0) + delta
        } else {
            for (entry in history.keys.sortedDescending()) {
                val removed = minOf(history[entry] ?: 0, -delta)
                history[entry] = (history[entry] ?: 0) - removed
                delta += removed
                if (delta == 0L) break
            }
        }
        return history.filterValues { it > 0 }
    }
}
