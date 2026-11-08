# Instrucciones de PrismV2

## Producto y alcance
- Prism es la aplicación principal. Conservar todas sus funcionalidades, preferencias y datos.
- Consultar docs/FEATURE_SCOPE.md: última selección y correcciones del propietario. No reactivar opciones desmarcadas ni considerar todo terminado por estar documentado.
- Prioridad de producto: logo y firma independientes del artista, edición local, API configurable, uso sin conexión y respaldo.
- N07/N09: estudiar integración en perfil de artista primero; usar área Catálogo como alternativa si evita correspondencias frágiles o una intervención excesiva.
- No implementar un catálogo unido sólo por nombre: preservar identidad, procedencia y posibilidad de corregir asociaciones.
- No prometer soporte para todos los formatos de animación ni resultados exactos de radio sin comprobarlos.

## Trabajo y revisión
- Comunicar en español sencillo qué cambió, qué se ejecutó y qué sigue pendiente.
- Trabajar en ramas descriptivas, nunca escribir directamente en main. PR enfocado; no fusionar ni publicar releases sin instrucciones.
- Leer CONTRIBUTING.md y estas instrucciones antes de modificar código.
- No suprimir tests, añadir continue-on-error o nuevos baselines para aparentar éxito.
- No tocar versiones, applicationId de producción, firma o canal de actualizaciones como efecto secundario.
- El propietario no quiere retirar ninguna función de Prism. Eliminar un elemento del trabajo nuevo NO significa eliminar su implementación existente.
- Las referencias de BitChord/MySpoti necesitan adaptación y revisión de licencia; no sustituir AppContainer, PlaybackService, Room o SettingsRepository completos.

## Arquitectura y convenciones
- Kotlin y Compose con estilo circundante; dependencia manual en AppContainer.kt, sin introducir Hilt/Dagger.
- Modelos/red en data/, persistencia Room en data/db; otros almacenes JSON y preferencias siguen siendo fuente de verdad para sus áreas.
- PlaybackService y PlayerConnection conservan el control de reproducción, audio focus y MediaSession.
- data/ArtistPrefs.kt conserva favoritos, estilo tipográfico de firma y color. Un nuevo logo o firma de imagen debe coexistir con esos datos.
- Respetar diseños, iconos generados desde design/icons y splitTranslations: no editar archivos generados.
- Textos nuevos localizados con placeholders consistentes; ejecutar TranslationsTest.
- Seguir las versiones actuales de Gradle/dependencias; no habilitar R8/minificación en releases. CONTRIBUTING explica dependencias de reflexión.
- Consultar límites y formatos antes de importar archivos; copiar recursos elegidos al almacenamiento propio, evitar rutas externas frágiles.
- Diferenciar logo de firma y selección local de respuesta API. Una actualización remota no sobrescribe una elección local.

## Compilación y pruebas
- Versión fuente: Gradle 9.8.0, AGP 9.4.1, Kotlin 2.4.20, compileSdk 37, minSdk 26.
- CI usa JDK 21; bytecode fuente Java 17. sdkmanager identifica SDK 37 como platforms;android-37.0.
- Comandos desde raíz:
  - ./gradlew :app:assembleDebug -PsideBySide=dev
  - ./gradlew :app:testDebugUnitTest -PsideBySide=dev
  - ./gradlew :app:lintDebug -PsideBySide=dev
- En Windows usar gradlew.bat.
- La suite original contiene pruebas contra servicios en vivo; investigar y distinguir fallo de red, fallo previo y regresión.
- No presentar compilación APK como prueba de arranque, audio real, cuentas o integraciones.
- Para cambios de artistas/backup: pruebas significativas de independencia logo/firma, prioridad local, borrado/restauración y nombres/IDs ambiguos.
- Pruebas instrumentadas requieren dispositivo/emulador; TV no es criterio de aceptación de esta etapa, pero su funcionalidad se conserva.

## Seguridad, privacidad y licencias
- CI de PR sin cookies, credenciales de cuentas o claves de distribución; contents: read y checkout sin credenciales persistentes.
- No usar pull_request_target para compilar código de PR. Actions fijadas por SHA.
- APK Dev firmado con clave desechable, instalable junto a Prism; nuevas ejecuciones pueden tener distinta firma.
- Nunca enviar cookies de YouTube a APIs de firmas, imágenes o servicios ajenos.
- Preservar licencias, autorías y avisos al adaptar referencias. BitChord contiene partes AGPL/LGPL además de su licencia general; revisar cada archivo.
- No añadir bibliotecas sin justificar y actualizar LicensesPage/assets cuando corresponda.
