package com.example.desafio_3_dsm

import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RatingBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import java.text.DateFormat
import java.util.Date

class MainActivity : AppCompatActivity() {
    private lateinit var content: LinearLayout
    private lateinit var toolbar: MaterialToolbar
    private val auth by lazy { FirebaseAuth.getInstance() }
    private var repository: ReadingRepository? = null
    private var activeUid: String? = null
    private var books = emptyList<Book>()
    private var goals = emptyMap<String, Int>()
    private var loaded = false
    private var dataError: String? = null
    private var screen = "home"
    private var formReturn = "home"
    private var editingId = ""
    private var draft: Bundle? = null
    private var captureDraft: (() -> Bundle)? = null
    private var authBusy = false
    private val authListener = FirebaseAuth.AuthStateListener { updateSession() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        content = findViewById(R.id.content)
        toolbar = findViewById(R.id.toolbar)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        screen = savedInstanceState?.getString("screen") ?: "home"
        formReturn = savedInstanceState?.getString("formReturn") ?: "home"
        editingId = savedInstanceState?.getString("editingId").orEmpty()
        draft = savedInstanceState?.getBundle("draft")
        activeUid = savedInstanceState?.getString("activeUid")
        toolbar.setNavigationOnClickListener { goBack() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (screen == "home" || screen == "login") {
                    moveTaskToBack(true)
                } else goBack()
            }
        })
    }

    override fun onStart() {
        super.onStart()
        auth.addAuthStateListener(authListener)
    }

    override fun onStop() {
        captureDraft?.let { draft = it() }
        auth.removeAuthStateListener(authListener)
        repository?.close()
        repository = null
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("screen", screen)
        outState.putString("formReturn", formReturn)
        outState.putString("editingId", editingId)
        outState.putBundle("draft", captureDraft?.invoke() ?: draft)
        outState.putString("activeUid", activeUid)
        super.onSaveInstanceState(outState)
    }

    private fun updateSession() {
        val uid = auth.currentUser?.uid
        if (uid != activeUid) {
            repository?.close()
            repository = null
            books = emptyList()
            goals = emptyMap()
            loaded = false
            dataError = null
            draft = null
            captureDraft = null
            editingId = ""
            screen = if (uid == null) "login" else "home"
            activeUid = uid
        }
        if (uid == null) {
            if (screen != "register") screen = "login"
            render()
            return
        }
        if (screen == "login" || screen == "register") screen = "home"
        if (repository == null) {
            repository = ReadingRepository(uid)
            repository!!.observe({ newBooks, newGoals ->
                if (auth.currentUser?.uid != uid) return@observe
                books = newBooks
                goals = newGoals
                loaded = true
                dataError = null
                if (screen != "edit" || captureDraft == null) render()
            }, { message ->
                if (auth.currentUser?.uid != uid) return@observe
                dataError = message
                if (screen != "edit") render() else toast(message)
            })
        }
        if (screen != "edit" || captureDraft == null) render()
    }

    private fun navigate(destination: String) {
        hideKeyboard()
        captureDraft = null
        draft = null
        screen = destination
        render()
        (content.parent as? android.widget.ScrollView)?.let { scroll -> scroll.post { scroll.scrollTo(0, 0) } }
    }

    private fun goBack() {
        if (screen == "edit") {
            MaterialAlertDialogBuilder(this)
                .setTitle("¿Salir del formulario?")
                .setMessage("Los cambios sin guardar se perderán.")
                .setNegativeButton("Continuar editando", null)
                .setPositiveButton("Salir") { _, _ -> navigate(formReturn) }
                .show()
        } else navigate(if (screen == "register") "login" else "home")
    }

    private fun render() {
        content.removeAllViews()
        captureDraft = null
        toolbar.menu.clear()
        toolbar.title = when (screen) {
            "login", "register" -> "Mis lecturas"
            "library" -> "Biblioteca"
            "stats" -> "Estadísticas"
            "edit" -> if (editingId.isEmpty()) "Agregar libro" else "Editar libro"
            else -> "Mis lecturas"
        }
        toolbar.navigationIcon = if (screen in listOf("home", "login")) null else
            androidx.appcompat.content.res.AppCompatResources.getDrawable(this, R.drawable.ic_back)
        toolbar.navigationContentDescription = "Volver"
        if (auth.currentUser != null) {
            toolbar.menu.add("Cerrar sesión").setOnMenuItemClickListener {
                MaterialAlertDialogBuilder(this).setTitle("Cerrar sesión")
                    .setMessage("Tus lecturas quedarán guardadas en tu cuenta.")
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Cerrar sesión") { _, _ ->
                        hideKeyboard()
                        repository?.close()
                        repository = null
                        auth.signOut()
                        updateSession()
                    }.show()
                true
            }
        }
        if (screen in listOf("login", "register")) {
            showAuth()
            return
        }
        if (dataError != null) {
            text(content, dataError!!)
            button(content, "Reintentar") {
                repository?.close()
                repository = null
                dataError = null
                updateSession()
            }
            return
        }
        if (!loaded) {
            text(content, "Cargando tus lecturas…")
            content.addView(ProgressBar(this))
            return
        }
        when (screen) {
            "library" -> showLibrary()
            "stats" -> showStatistics()
            "edit" -> showBookForm()
            else -> showHome()
        }
    }

    private fun showAuth() {
        val register = screen == "register"
        heading(content, if (register) "Crea tu cuenta" else "Bienvenido")
        text(content, "Organiza tus libros y sigue tu meta de lectura.")
        val email = field(content, "Correo electrónico", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        val password = field(content, "Contraseña", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        password.box.endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        val confirmation = if (register) field(content, "Confirmar contraseña", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD).also {
            it.box.endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        } else null
        text(content, "La contraseña debe tener al menos 6 caracteres.", 13)
        val error = text(content, "", 14).apply {
            setTextColor(android.graphics.Color.rgb(160, 35, 35))
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        lateinit var submit: MaterialButton
        lateinit var switch: MaterialButton
        submit = button(content, if (register) "Registrarme" else "Iniciar sesión") {
            email.box.error = null
            password.box.error = null
            confirmation?.box?.error = null
            error.text = ""
            val address = email.value()
            val secret = password.input.text.toString()
            var valid = true
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(address).matches()) {
                email.box.error = "Escribe un correo electrónico válido."
                valid = false
            }
            if (secret.length < 6) {
                password.box.error = "La contraseña debe tener al menos 6 caracteres."
                valid = false
            }
            if (confirmation != null && secret != confirmation.input.text.toString()) {
                confirmation.box.error = "Las contraseñas no coinciden."
                valid = false
            }
            if (valid && !authBusy) {
                hideKeyboard()
                authBusy = true
                submit.isEnabled = false
                switch.isEnabled = false
                submit.text = "Espera un momento…"
                val operation = if (register) auth.createUserWithEmailAndPassword(address, secret) else auth.signInWithEmailAndPassword(address, secret)
                operation.addOnCompleteListener { result ->
                    authBusy = false
                    submit.isEnabled = true
                    switch.isEnabled = true
                    submit.text = if (register) "Registrarme" else "Iniciar sesión"
                    if (!result.isSuccessful) {
                        error.text = authError(result.exception)
                    }
                }
            }
        }
        switch = button(content, if (register) "Ya tengo una cuenta" else "Crear una cuenta", true) {
            navigate(if (register) "login" else "register")
        }
        submit.isEnabled = !authBusy
        switch.isEnabled = !authBusy
    }

    private fun showHome() {
        val year = ReadingRules.year()
        heading(content, "Hola,")
        text(content, auth.currentUser?.email.orEmpty(), 17)
        val completed = ReadingRules.finishedThisYear(books, year).size
        val goal = goals[year.toString()]
        panel(content) { box ->
            heading(box, "Tu meta de $year", 20)
            text(box, if (goal == null) "Todavía no has definido una meta." else "$completed de $goal libros terminados", 18)
            if (goal == null) text(box, "$completed libros terminados este año.")
            if (goal != null) {
                progress(box, (completed.toLong() * 100 / goal).coerceAtMost(100).toInt())
                text(box, if (completed >= goal) "Has cumplido tu meta anual." else "Te faltan ${goal - completed} libros.")
            }
            button(box, if (goal == null) "Definir meta" else "Modificar meta", true) { showGoalDialog() }
        }
        button(content, "Agregar libro") { openBook(null) }
        button(content, "Ver biblioteca", true) { navigate("library") }
        button(content, "Ver estadísticas", true) { navigate("stats") }
        heading(content, "Leyendo actualmente", 20)
        val reading = books.filter { it.status == "Leyendo" }
        if (reading.isEmpty()) text(content, "Aún no tienes libros en lectura. Agrega uno para comenzar.")
        reading.sortedBy { it.title.lowercase() }.forEach { bookCard(it) }
    }

    private fun showGoalDialog() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), 0, dp(24), 0) }
        val goal = field(box, "Libros para ${ReadingRules.year()}", InputType.TYPE_CLASS_NUMBER)
        goal.input.setText(goals[ReadingRules.year().toString()]?.toString().orEmpty())
        val dialog = MaterialAlertDialogBuilder(this).setTitle("Meta anual").setView(box)
            .setNegativeButton("Cancelar", null).setPositiveButton("Guardar", null).create()
        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val number = goal.value().toIntOrNull()
                if (number == null || number <= 0) {
                    goal.box.error = "La meta debe ser un número entero mayor que cero."
                } else {
                    repository?.saveGoal(ReadingRules.year(), number) { toast("No se guardó la meta en Firebase. Revisa la conexión y las reglas.") }
                    dialog.dismiss()
                    toast("Meta guardada. Se sincronizará al tener conexión.")
                }
            }
        }
        dialog.show()
    }

    private fun showLibrary() {
        heading(content, "Todos tus libros", 22)
        text(content, "${books.size} libros en tu biblioteca")
        button(content, "Agregar libro") { openBook(null) }
        if (books.isEmpty()) text(content, "Tu biblioteca está vacía. Agrega tu primer libro.")
        books.sortedBy { it.title.lowercase() }.forEach { bookCard(it) }
    }

    private fun bookCard(book: Book) {
        panel(content) { box ->
            heading(box, book.title, 20)
            text(box, book.author)
            text(box, "${book.genre} · ${book.status}", 14)
            progress(box, book.progress)
            text(box, "${book.pagesRead} de ${book.totalPages} páginas · ${book.progress} %", 14)
            if (book.status == "Terminado") {
                text(box, "Calificación: ${book.rating} de 5 estrellas")
                text(box, "Terminado el ${DateFormat.getDateInstance().format(Date(book.finishedAt))}", 13)
            }
            button(box, "Editar libro", true) { openBook(book) }
        }
    }

    private fun showStatistics() {
        val year = ReadingRules.year()
        val finished = ReadingRules.finishedThisYear(books, year)
        heading(content, "Tus lecturas de $year", 22)
        text(content, "${finished.size} libros terminados")
        panel(content) { box ->
            heading(box, "Libros por género", 20)
            val counts = Book.GENRES.associateWith { genre -> finished.count { it.genre == genre } }
            counts.forEach { (genre, count) -> text(box, "$genre: $count") }
            val maximum = counts.values.maxOrNull() ?: 0
            text(box, if (maximum == 0) "Género más leído: sin lecturas terminadas." else
                "Género más leído: " + counts.filterValues { it == maximum }.keys.joinToString(", "))
        }
        panel(content) { box ->
            heading(box, "Páginas leídas", 20)
            val pages = books.sumOf { it.pagesByYear[year.toString()] ?: 0L }
            text(box, "$pages páginas en $year", 22)
            text(box, "Se cuentan los avances registrados este año, incluidos los libros en curso. Las correcciones descuentan las páginas más recientes.", 13)
        }
        panel(content) { box ->
            heading(box, "Libro mejor calificado", 20)
            val best = ReadingRules.bestBook(books, year)
            if (best == null) text(box, "Termina y califica un libro para verlo aquí.")
            else {
                text(box, best.title, 19)
                text(box, best.author)
                text(box, "${best.rating} de 5 estrellas")
                text(box, "Terminado el ${DateFormat.getDateInstance().format(Date(best.finishedAt))}", 13)
            }
        }
    }

    private fun openBook(book: Book?) {
        formReturn = screen
        editingId = book?.id.orEmpty()
        navigate("edit")
    }

    private fun showBookForm() {
        val previous = books.find { it.id == editingId }
        val values = draft
        val title = field(content, "Título")
        val author = field(content, "Autor")
        val genre = selector(content, "Género", Book.GENRES)
        val total = field(content, "Total de páginas", InputType.TYPE_CLASS_NUMBER)
        val read = field(content, "Páginas leídas", InputType.TYPE_CLASS_NUMBER)
        val status = selector(content, "Estado", Book.STATUSES)
        text(content, "Calificación", 16)
        val stars = RatingBar(this).apply { numStars = 5; stepSize = 1f; contentDescription = "Calificación de 1 a 5 estrellas" }
        content.addView(stars, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        val ratingError = text(content, "", 14).apply { setTextColor(android.graphics.Color.rgb(160, 35, 35)) }
        val hint = text(content, "La calificación es obligatoria solo al terminar el libro.", 13)
        title.input.setText(values?.getString("title") ?: previous?.title.orEmpty())
        author.input.setText(values?.getString("author") ?: previous?.author.orEmpty())
        total.input.setText(values?.getString("total") ?: previous?.totalPages?.toString().orEmpty())
        read.input.setText(values?.getString("read") ?: previous?.pagesRead?.toString() ?: "0")
        genre.setSelection((values?.getInt("genre") ?: Book.GENRES.indexOf(previous?.genre ?: Book.GENRES.first())).coerceAtLeast(0))
        val initialStatus = values?.getInt("status") ?: Book.STATUSES.indexOf(previous?.status ?: "Por leer")
        status.setSelection(initialStatus.coerceAtLeast(0))
        stars.rating = values?.getFloat("rating") ?: previous?.rating?.toFloat() ?: 0f
        var lastStatus = initialStatus
        fun updateRating() {
            val finished = status.selectedItem == "Terminado"
            stars.isEnabled = finished
            stars.alpha = if (finished) 1f else 0.35f
            if (!finished) stars.rating = 0f
            ratingError.text = ""
            hint.text = if (finished) "Selecciona entre 1 y 5 estrellas." else "Disponible al marcar el libro como terminado."
        }
        updateRating()
        status.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (lastStatus != position) {
                    if (Book.STATUSES[position] == "Terminado" && total.value().toIntOrNull()?.let { it > 0 } == true) read.input.setText(total.value())
                    if (Book.STATUSES[position] == "Por leer") read.input.setText("0")
                    lastStatus = position
                }
                updateRating()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        captureDraft = {
            Bundle().apply {
                putString("title", title.input.text.toString())
                putString("author", author.input.text.toString())
                putString("total", total.input.text.toString())
                putString("read", read.input.text.toString())
                putInt("genre", genre.selectedItemPosition)
                putInt("status", status.selectedItemPosition)
                putFloat("rating", stars.rating)
            }
        }
        button(content, "Guardar libro") {
            listOf(title, author, total, read).forEach { it.box.error = null }
            val selectedStatus = status.selectedItem.toString()
            val errors = ReadingRules.validate(title.value(), author.value(), total.value(), read.value(), selectedStatus, stars.rating.toInt())
            title.box.error = errors["title"]
            author.box.error = errors["author"]
            total.box.error = errors["total"]
            read.box.error = errors["read"]
            ratingError.text = errors["rating"].orEmpty()
            if (errors.isEmpty()) {
                val pagesRead = read.value().toInt()
                val book = Book(
                    title = title.value(),
                    author = author.value(),
                    genre = genre.selectedItem.toString(),
                    totalPages = total.value().toInt(),
                    pagesRead = pagesRead,
                    status = selectedStatus,
                    rating = if (selectedStatus == "Terminado") stars.rating.toInt() else 0,
                    finishedAt = if (selectedStatus == "Terminado") {
                        previous?.finishedAt?.takeIf { previous.status == "Terminado" && it > 0 } ?: System.currentTimeMillis()
                    } else 0,
                    pagesByYear = ReadingRules.pageHistory(previous, pagesRead, ReadingRules.year()),
                    id = editingId
                )
                repository?.saveBook(book) { toast("Firebase no pudo guardar el libro. Revisa la conexión y las reglas.") }
                navigate(formReturn)
                toast("Libro guardado. Se sincronizará al tener conexión.")
            } else {
                listOf(title, author, total, read).firstOrNull { it.box.error != null }?.input?.requestFocus()
            }
        }
        button(content, "Cancelar", true) { goBack() }
    }

    private data class Field(val box: TextInputLayout, val input: TextInputEditText) {
        fun value() = input.text.toString().trim()
    }

    private fun field(parent: LinearLayout, label: String, type: Int = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES): Field {
        val box = TextInputLayout(this).apply { hint = label; boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE }
        val input = TextInputEditText(box.context).apply {
            inputType = type
            isSingleLine = true
            id = View.generateViewId()
        }
        box.addView(input)
        parent.addView(box, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        return Field(box, input)
    }

    private fun selector(parent: LinearLayout, label: String, items: List<String>): Spinner {
        val labelView = text(parent, label, 14)
        return Spinner(this).apply {
            id = View.generateViewId()
            contentDescription = label
            labelView.labelFor = id
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, items).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            parent.addView(this, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(10) })
        }
    }

    private fun text(parent: LinearLayout, value: String, size: Int = 16): TextView = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(android.graphics.Color.rgb(44, 49, 44))
        setLineSpacing(dp(3).toFloat(), 1f)
        parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
    }

    private fun heading(parent: LinearLayout, value: String, size: Int = 26) {
        text(parent, value, size).setTypeface(null, Typeface.BOLD)
    }

    private fun button(parent: LinearLayout, label: String, outlined: Boolean = false, action: () -> Unit): MaterialButton {
        return MaterialButton(this, null, if (outlined) com.google.android.material.R.attr.materialButtonOutlinedStyle else com.google.android.material.R.attr.materialButtonStyle).apply {
            text = label
            isAllCaps = false
            cornerRadius = dp(10)
            minHeight = dp(48)
            parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
            setOnClickListener { action() }
        }
    }

    private fun panel(parent: LinearLayout, build: (LinearLayout) -> Unit) {
        val card = MaterialCardView(this).apply {
            radius = dp(12).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1)
            setStrokeColor(android.graphics.Color.rgb(222, 224, 216))
        }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(10)) }
        card.addView(box)
        parent.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8); bottomMargin = dp(12) })
        build(box)
    }

    private fun progress(parent: LinearLayout, value: Int) {
        parent.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = value
            contentDescription = "Avance de lectura: $value por ciento"
        }, LinearLayout.LayoutParams(-1, dp(12)).apply { bottomMargin = dp(8) })
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    private fun hideKeyboard() {
        currentFocus?.let { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(it.windowToken, 0) }
    }

    private fun authError(error: Exception?): String {
        if (error is FirebaseNetworkException) return "No hay conexión. Revisa tu acceso a internet."
        return when ((error as? FirebaseAuthException)?.errorCode) {
            "ERROR_EMAIL_ALREADY_IN_USE" -> "Este correo ya tiene una cuenta. Inicia sesión."
            "ERROR_INVALID_EMAIL" -> "El correo electrónico no es válido."
            "ERROR_WEAK_PASSWORD" -> "La contraseña no cumple los requisitos de seguridad."
            "ERROR_USER_DISABLED" -> "Esta cuenta está deshabilitada."
            "ERROR_TOO_MANY_REQUESTS" -> "Demasiados intentos. Espera unos minutos."
            "ERROR_OPERATION_NOT_ALLOWED" -> "Activa Correo electrónico/contraseña en Firebase Authentication."
            "ERROR_INVALID_CREDENTIAL", "ERROR_INVALID_LOGIN_CREDENTIALS", "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND" -> "Correo o contraseña incorrectos."
            else -> "No se pudo autenticar. Comprueba tus datos y la configuración de Firebase."
        }
    }
}
