package com.example.desafio_3_dsm

import android.graphics.Bitmap
import android.view.View
import android.widget.RatingBar
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.*
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import org.hamcrest.Matchers.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ReadingFlowTest {
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()
    private val evidenceFolder = "Pictures/MisLecturasEvidencias_${System.currentTimeMillis()}/"
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun waitFor(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 30000
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("La operación no terminó en 30 segundos")
            Thread.sleep(200)
        }
        instrumentation.waitForIdleSync()
    }

    private fun screenshot(name: String) {
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        instrumentation.waitForIdleSync()
        Thread.sleep(if (name in listOf("06_principal", "10_estadisticas")) 4000 else 500)
        val resolver = instrumentation.targetContext.contentResolver
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, evidenceFolder)
        }
        val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
        resolver.openOutputStream(uri)!!.use {
            instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun clickText(text: String) = onView(withText(text)).perform(scrollTo(), click())
    private fun fill(hint: String, text: String) = onView(withHint(hint)).perform(scrollTo(), replaceText(text), androidx.test.espresso.action.ViewActions.closeSoftKeyboard())

    @Test
    fun realFirebaseFlowAndEvidence() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("liveFirebase") == "true")
        auth.signOut()
        val email = "lecturas.prueba.${System.currentTimeMillis()}@example.com"
        val password = "Lecturas2026!"
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        var firstUid: String? = null
        try {
            screenshot("01_inicio_sesion")
            clickText("Crear una cuenta")
            screenshot("02_registro")
            clickText("Registrarme")
            onView(withText("Escribe un correo electrónico válido.")).check(matches(isDisplayed()))
            screenshot("03_validacion_registro")
            fill("Correo electrónico", email)
            fill("Contraseña", password)
            fill("Confirmar contraseña", password)
            clickText("Registrarme")
            waitFor { auth.currentUser != null }
            firstUid = auth.currentUser!!.uid
            val root = database.getReference("users").child(firstUid)
            Tasks.await(root.child("goals").child(ReadingRules.year().toString()).setValue(12), 30, TimeUnit.SECONDS)
            waitFor { runCatching { onView(withText("Agregar libro")).check(matches(isDisplayed())); true }.getOrDefault(false) }
            screenshot("04_principal_vacia")
            clickText("Agregar libro")
            fill("Título", "El principito")
            fill("Autor", "Antoine de Saint Exupéry")
            fill("Total de páginas", "120")
            onView(withContentDescription("Estado")).perform(scrollTo(), click())
            onData(equalTo("Leyendo")).perform(click())
            fill("Páginas leídas", "45")
            screenshot("05_registro_libro")
            clickText("Guardar libro")
            waitFor { Tasks.await(root.child("books").get(), 5, TimeUnit.SECONDS).childrenCount == 1L }
            val saved = Tasks.await(root.child("books").get(), 10, TimeUnit.SECONDS).children.first().getValue(Book::class.java)!!
            assertEquals("El principito", saved.title)
            assertEquals(37, saved.progress)
            val now = System.currentTimeMillis()
            val samples = listOf(
                Book("Breve historia del mundo", "Ernst Gombrich", "Historia", 300, 300, "Terminado", 5, now - 100000, mapOf(ReadingRules.year().toString() to 300L)),
                Book("Hábitos atómicos", "James Clear", "Desarrollo personal", 320, 320, "Terminado", 5, now - 50000, mapOf(ReadingRules.year().toString() to 320L)),
                Book("La máquina del tiempo", "H G Wells", "Ciencia ficción", 180, 180, "Terminado", 4, now, mapOf(ReadingRules.year().toString() to 180L))
            )
            samples.forEach { Tasks.await(root.child("books").push().setValue(it), 10, TimeUnit.SECONDS) }
            Thread.sleep(800)
            scenario.recreate()
            waitFor { runCatching { onView(withText("3 de 12 libros terminados")).check(matches(isDisplayed())); true }.getOrDefault(false) }
            screenshot("06_principal")
            clickText("Modificar meta")
            screenshot("07_meta")
            onView(withText("Cancelar")).perform(click())
            clickText("Ver biblioteca")
            screenshot("08_biblioteca")
            onView(allOf(withText("Editar libro"), withParent(hasDescendant(withText("El principito"))))).perform(scrollTo(), click())
            fill("Título", "El principito editado")
            onView(withContentDescription("Estado")).perform(scrollTo(), click())
            onData(equalTo("Terminado")).perform(click())
            onView(isAssignableFrom(RatingBar::class.java)).perform(scrollTo(), object : androidx.test.espresso.ViewAction {
                override fun getConstraints() = isAssignableFrom(RatingBar::class.java)
                override fun getDescription() = "Asignar cinco estrellas"
                override fun perform(controller: androidx.test.espresso.UiController, view: View) { (view as RatingBar).rating = 5f }
            })
            screenshot("09_libro_terminado")
            clickText("Guardar libro")
            Thread.sleep(700)
            androidx.test.espresso.Espresso.pressBack()
            clickText("Ver estadísticas")
            screenshot("10_estadisticas")
            val stored = Tasks.await(root.child("books").get(), 10, TimeUnit.SECONDS).children.mapNotNull { it.getValue(Book::class.java) }
            assertEquals(4, ReadingRules.finishedThisYear(stored, ReadingRules.year()).size)
            assertEquals("Breve historia del mundo", ReadingRules.bestBook(stored, ReadingRules.year())?.title)
            assertEquals(920L, stored.sumOf { it.pagesByYear[ReadingRules.year().toString()] ?: 0L })
            scenario.recreate()
            waitFor { runCatching { onView(withText("4 libros terminados")).check(matches(isDisplayed())); true }.getOrDefault(false) }
            scenario.close()
            auth.signOut()
            Tasks.await(auth.signInWithEmailAndPassword(email, password), 30, TimeUnit.SECONDS)
            assertEquals(firstUid, auth.currentUser!!.uid)
            Tasks.await(root.removeValue(), 15, TimeUnit.SECONDS)
            Tasks.await(auth.currentUser!!.delete(), 15, TimeUnit.SECONDS)
            firstUid = null
            Tasks.await(auth.createUserWithEmailAndPassword("segunda.${System.currentTimeMillis()}@example.com", password), 30, TimeUnit.SECONDS)
            val other = database.getReference("users").child("otra-cuenta-prueba-aislamiento")
            val denied = runCatching { Tasks.await(other.get(), 10, TimeUnit.SECONDS) }.isFailure
            Tasks.await(auth.currentUser!!.delete(), 15, TimeUnit.SECONDS)
            assertTrue("Las reglas deben rechazar la lectura de otro UID", denied)
        } finally {
            scenario.close()
            if (firstUid != null && auth.currentUser?.uid == firstUid) {
                runCatching { Tasks.await(database.getReference("users").child(firstUid).removeValue(), 10, TimeUnit.SECONDS) }
                runCatching { Tasks.await(auth.currentUser!!.delete(), 10, TimeUnit.SECONDS) }
            }
            auth.signOut()
        }
    }
}



