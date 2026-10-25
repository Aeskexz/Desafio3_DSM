# Mis lecturas

Aplicación Android sencilla en Kotlin para registrar lecturas con Firebase Authentication y Realtime Database.

## Abrir y ejecutar

Abre esta carpeta en Android Studio, sincroniza Gradle y ejecuta el módulo app. El identificador Android es com.example.desf_3_dsm y coincide con app/google-services.json.

Firebase debe tener habilitado Authentication > Método de acceso > Correo electrónico/contraseña.

En Realtime Database > Reglas, publica el contenido de database.rules.json. El archivo local no cambia las reglas de la consola automáticamente. Las reglas deben comparar auth.uid con $uid; comprobar solamente auth != null no aísla las cuentas.

## Uso

Crea una cuenta o inicia sesión. Define una meta anual positiva, agrega libros y modifica las páginas leídas desde Editar libro. Al elegir Terminado se completan las páginas y debes seleccionar de una a cinco estrellas. Al volver a editar un libro terminado se conserva su fecha original; si vuelve a otro estado, la fecha y la calificación se eliminan.

La biblioteca incluye todos los libros y permite editarlos. El inicio muestra los que están en lectura. Las estadísticas cuentan los libros terminados en el año actual, todos los géneros empatados como más leídos y el libro mejor calificado; en empate, gana el que terminó primero.

Las páginas del año se calculan a partir de los avances registrados ese año, incluyendo libros en curso. La carga inicial de páginas se atribuye al año del registro. Las correcciones hacia abajo descuentan las páginas más recientes. No se registran relecturas independientes.

## Datos

- users/UID/books/ID: título, autor, género, páginas, estado, calificación, fecha y páginas por año.
- users/UID/goals/AÑO: meta anual.
- Firebase conserva la sesión y mantiene una caché local persistente de Realtime Database. Los cambios sin conexión se sincronizan al recuperar internet.
- La creación de cuentas y el inicio de sesión requieren internet.

## Verificación

En Windows, usa el JBR de Android Studio como JAVA_HOME.

    ./gradlew.bat assembleDebug testDebugUnitTest lintDebug
    ./gradlew.bat connectedDebugAndroidTest

La prueba de integración real es opcional. Crea cuentas temporales en Firebase, captura las pantallas, verifica lecturas y prueba el rechazo de otros UID; al finalizar elimina las cuentas y sus datos de prueba.

    ./gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.liveFirebase=true"

Las capturas se guardan en el emulador en Pictures/MisLecturasEvidencias seguido del identificador de la ejecución.

## Entrega

El documento de evidencias está en output/pdf. Agrega tu enlace real del repositorio antes de entregar. No se publica el proyecto automáticamente.
