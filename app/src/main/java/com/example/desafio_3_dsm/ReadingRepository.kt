package com.example.desafio_3_dsm

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class ReadingRepository(uid: String) {
    private val reference = FirebaseDatabase.getInstance().getReference("users").child(uid)
    private var listener: ValueEventListener? = null

    fun observe(onData: (List<Book>, Map<String, Int>) -> Unit, onError: (String) -> Unit) {
        reference.keepSynced(true)
        listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val books = snapshot.child("books").children.mapNotNull { item ->
                    item.getValue(Book::class.java)?.apply { id = item.key.orEmpty() }
                }
                val goals = snapshot.child("goals").children.mapNotNull {
                    val key = it.key
                    val value = it.getValue(Int::class.java)
                    if (key != null && value != null) key to value else null
                }.toMap()
                onData(books, goals)
            }

            override fun onCancelled(error: DatabaseError) {
                onError(if (error.code == DatabaseError.PERMISSION_DENIED) {
                    "Firebase rechazó el acceso. Revisa las reglas de /users/tu-uid en Realtime Database."
                } else "No se pudieron cargar tus datos. Comprueba tu conexión e inténtalo de nuevo.")
            }
        }
        reference.addValueEventListener(listener!!)
    }

    fun saveBook(book: Book, onError: () -> Unit): String {
        val target: DatabaseReference = if (book.id.isBlank()) reference.child("books").push() else reference.child("books").child(book.id)
        target.setValue(book).addOnFailureListener { onError() }
        return target.key.orEmpty()
    }

    fun saveGoal(year: Int, goal: Int, onError: () -> Unit) {
        reference.child("goals").child(year.toString()).setValue(goal).addOnFailureListener { onError() }
    }

    fun close() {
        listener?.let { reference.removeEventListener(it) }
        reference.keepSynced(false)
        listener = null
    }
}
