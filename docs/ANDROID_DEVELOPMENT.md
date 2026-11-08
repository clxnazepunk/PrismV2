# Desarrollo Android de PrismV2

## Compilar
El proyecto mantiene su Gradle wrapper 9.8.0, AGP 9.4.1 y Kotlin 2.4.20.
CONTRIBUTING indica JDK 17+; el entorno CI usa Temurin 21 sin cambiar el
destino de bytecode Java 17. Instalar SDK 37 con el identificador
platforms;android-37.0 y build-tools 36.0.0; Gradle puede resolver herramientas
adicionales declaradas por el plugin.

Con SDK configurado en ANDROID_HOME o local.properties:
```sh
./gradlew :app:assembleDebug -PsideBySide=dev
./gradlew :app:testDebugUnitTest -PsideBySide=dev
./gradlew :app:lintDebug -PsideBySide=dev
```

En Windows usar gradlew.bat. No hacen falta credenciales Last.fm para compilar.
El APK de prueba usa com.prism.music.dev y se instala junto a la app normal.
No cambia ni sustituye la clave de distribución original.

## GitHub Actions
Abrir Actions > Prism Android. El flujo se activa en PRs y main; también admite
ejecución manual cuando esté disponible en la rama predeterminada.
Tiene jobs independientes de APK, pruebas y lint, sin secretos de cuentas o
distribución. Se preservan los fallos; producir APK no significa aprobar tests.
setup-gradle valida el wrapper y gestiona caché.

Descargar prism-dev-debug-<número>, extraer y usar el APK. checksums.sha256
permite verificar integridad. Artifacts durante 14 días.
Cada ejecución puede usar clave debug distinta: no recomendar desinstalar una
copia anterior sin advertir que podría borrar datos.

Si el fork tiene Actions desactivado, el propietario debe habilitarlo.
Pruebas de servicios en vivo pueden fallar por disponibilidad/autenticación;
no ignorar esos resultados. Los informes se guardan aunque falle el job.

## Qué falta comprobar aparte
Instalación y arranque, cuentas, reproducción real, UI, dispositivos externos,
backups/restauración y pruebas instrumentadas. No se instalan emuladores ni
se modifica la firma de producción en esta preparación. TV se conserva pero
no es criterio de aceptación para la primera etapa Android teléfono.

Ver docs/FEATURE_SCOPE.md para alcance y AGENTS.md para convenciones.
