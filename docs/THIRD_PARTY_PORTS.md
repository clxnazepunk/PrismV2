# Procedencia de logos y firmas

El cliente de fuentes de artistas adapta la lógica de estos archivos de BitChord,
revisión acf219a7e4a7f52f026d4336d5c7ac5cf7bfafcc:

- [ArtistSignatures.kt](https://github.com/kushagrasinghx/BitChord/blob/acf219a7e4a7f52f026d4336d5c7ac5cf7bfafcc/app/src/main/java/com/music/bitchord/data/canvas/ArtistSignatures.kt).
- [AppleArtistArt.kt](https://github.com/kushagrasinghx/BitChord/blob/acf219a7e4a7f52f026d4336d5c7ac5cf7bfafcc/app/src/main/java/com/music/bitchord/data/canvas/AppleArtistArt.kt).
- [AppleCatalogue.kt](https://github.com/kushagrasinghx/BitChord/blob/acf219a7e4a7f52f026d4336d5c7ac5cf7bfafcc/shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/AppleCatalogue.kt).

Autores: colaboradores de BitChord. README y licencia raíz declaran GNU GPL v3.
La adaptación se distribuye bajo GPL v3, compatible con la elección de GPL v3
que permite Prism. No se presume permiso de relicenciar esa parte bajo versiones
posteriores. Se conserva la atribución en el código y la pantalla de licencias.
No se han importado los motores de reproducción ni los módulos AGPL/LGPL de BitChord.

La adaptación conserva búsqueda de firmas, rechazo de respuesta ambigua,
selección por licencia/formato y comprobación del catálogo para logos Apple.
Se separó del modelo/red de BitChord, y se añadió almacenamiento por identificador
de artista, copias con rutas relativas y prioridad de imágenes locales.

No existe una colección de imágenes incluida en esos archivos Kotlin.
Los recursos se obtienen de los proveedores al abrir perfiles públicos.
La API configurada por defecto es https://artistsignature.velixir.run.
Se comprobó health HTTP 200 y una respuesta real de firmas SVG/PNG el
8 de noviembre de 2026; eso no garantiza disponibilidad o cobertura futura.
Las páginas públicas de Apple también respondieron, pero no todos sus artistas
tienen logo. La identidad se verifica con nombres y títulos del catálogo.

Los derechos de cada imagen son distintos de la licencia del programa.
La aplicación guarda enlace de procedencia y licencia comunicada por la API;
no incluye esas imágenes en el APK ni afirma que los logos de Apple sean libres.

Se añade coil-svg 3.6.3 (misma versión que Coil ya presente), Apache 2.0.
Su dependencia AndroidSVG 1.4 es Apache 2.0; ambas figuran en LicensesPage y
usan el texto Apache ya incluido en assets/licenses/apache-2.0.txt.
[Documentación oficial SVG](https://coil-kt.github.io/coil/svgs/).

Pruebas deterministas de parsers, identidades, prioridad local, datos inválidos,
restauración y límites. Las consultas reales no forman parte de esos tests.
