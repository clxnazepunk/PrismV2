# Diagnóstico preliminar y orden de trabajo

8 de noviembre de 2026. Base de producto: Prism 1.8.2.1, commit
1abbed19d374b84e6cda4e1c63db937d1f70befd. Es una estimación revisable;
no confirma cuentas, servicios externos ni comportamiento en un teléfono.

## Lo que ya tiene base real

- B07: Settings.kt guarda audioQuality y cellularQuality; SettingsScreen.kt
  ofrece las dos elecciones. Conservar y verificar, no volver a importarlo.
- B04: SpotifySettings.kt permite conexión propia y opciones de listas,
  favoritos, actividad reciente y artistas principales. No equivale a reproducción
  nativa Spotify. Verificar con cuenta antes de afirmar equivalencia completa.
- N06: Uploads.kt, UploadsScreen.kt y Song.isUpload ya existen. Preservar
  identificadores privados. Pendiente comprobar las tres vistas con una cuenta.
- N04: OfflineArtists.kt / ArtistOpened reconstruyen perfiles desde el teléfono;
  falta ampliar logos y firmas de imagen independientes.
- N05: Backup.kt incluye artist_prefs, playlist_covers y files/covers en
  LIBRARY. Reutilizar antes de crear otro formato.
- N08: Models.kt / Releases ofrece yearOf, newestFirst y oldestFirst.
  Agrupar años y unir subidos es trabajo adicional.
- B01: CanvasRepository, CanvasStore y CanvasPlayer ya buscan, guardan y reproducen
  animaciones. No prueban acceso al Canvas específico de Spotify ni un selector
  entre distintas fuentes.
- N03/B02: ArtistPrefs.kt almacena estilos tipográficos de firma; no son dos
  imágenes independientes de logo y firma.

## Orden aproximado: menor a mayor dificultad

| Orden | Función o parte | Dificultad | Principal motivo y riesgo |
| --- | --- | --- | --- |
| 0 | Conservar/verificar B07, B04 y base N06/N12 | Ya existe total o parcialmente | No duplicar ajustes, conexión Spotify, uploads o radio existentes. Las cuentas no están verificadas. |
| 1 | N10: sugerencias en playlists locales | FÁCIL | Reutiliza AppContainer.radio y PrismLibrary.add; no cambia DB ni cuenta. Recomendaciones externas pueden ser escasas. |
| 2 | N05: respaldo de imágenes estáticas nuevas | FÁCIL una vez definido N03 | Usar archivos/prefs ya incluidos; comprobar restauración y asociaciones. |
| 3 | N03: logo y firma locales independientes | MEDIA | ArtistScreen, ArtistPrefs y PlaylistCovers; conservar tipografía, transparencia y proporción. Es prioridad de producto tras el primer cambio acotado. |
| 4 | N01: calidad elegible de imágenes | MEDIA | hiRes en Models.kt recibe tamaños desde muchas vistas; elección consistente, caché y ausencia de alta resolución requieren cuidado. |
| 5 | B08: tarjetas de letras personalizables | MEDIA | Revisar la experiencia de letras y captura existente antes de ampliar estilos; necesita revisión visual. |
| 6 | N04: perfiles con los nuevos recursos offline | MEDIA | Ampliar OfflineArtists y respaldo sin cambiar la identidad de artista. |
| 7 | N10: sugerencias en playlists de YouTube editables | MEDIA | Permisos, lista cargada por completo, confirmación de escritura remota y refresco; no basta añadir un botón. |
| 8 | N08: agrupación cronológica de álbumes actuales | MEDIA | Ya hay orden por año; tratar fechas ausentes y reediciones sin inventar fechas. |
| 9 | N12/M01: mejorar radio sucesiva si falta flujo | MEDIA, pendiente de diferencias | Reutilizar radio actual; no crear otro algoritmo ni prometer rareza. |
| 10 | B02/N03: proveedor API y descarga de recursos | MEDIA / INCIERTA externa | BitChord ArtistSignatures.kt es referencia real; falta comprobar disponibilidad, respuestas, derechos y almacenamiento offline. |
| 11 | N02: portadas locales animadas y respaldo | DIFÍCIL | GIF/imágenes y vídeo son rutas diferentes; batería, tamaños, ciclos de vida y caché. No existe soporte universal garantizado. |
| 12 | B01: elegir Canvas o portada animada | DIFÍCIL / INCIERTA externa | Hay reproductor, pero faltan identidad de fuente, selección y una fuente Canvas comprobada. |
| 13 | N07: oficiales + subidos por artista | DIFÍCIL | Identificadores distintos, ambigüedad de nombres y metadatos privados; Perfil primero, Catálogo si reduce fragilidad. |
| 14 | N09 y N08 sobre catálogo combinado | DIFÍCIL | Dependen de N07 y de metadatos reales de N06; búsqueda/cronología sin alterar originales. |

N11 sigue fuera del alcance activo. B03, B05, B06 y B09 se retiran como
trabajo nuevo, sin borrar ninguna función de Prism. Todas las demás se conservan.

## Primer cambio implementado para revisión

N10, etapa local: al llegar al final de una playlist de Prism se consultan hasta
tres radios de canciones públicas de esa lista. Hasta diez resultados únicos
pueden añadirse al final mediante un botón. Sin envíos de ids de archivos
locales, subidos privados, podcasts o directos; sin escribir listas en YouTube.
No garantiza diez resultados ni una calidad determinada de recomendaciones.
No reproduce música hasta que el usuario toca una canción.

PlaylistSuggestionsTest comprueba exclusión, límites, fallos parciales,
cancelación y persistencia del orden al volver a abrir la lista. La compilación
y sus resultados se documentan en el PR; no confundir prueba escrita con prueba aprobada.

## Base de verificaciones

El APK Dev de preparación compiló. Lint encontró 98 errores, 334 advertencias
y 18 sugerencias en el código de partida; primer hallazgo en WebRemote.kt:83.
No se han ocultado ni suprimido. La suite original contiene pruebas de red.
Instalación, revisión visual, reproducción y cuentas reales siguen pendientes.

## Licencias y procedencia

Este primer cambio usa sólo componentes de Prism; no copia código de MySpoti o
BitChord. Sus proyectos sirven como referencia de experiencia. Antes de portar
un archivo o recurso se revisarán licencia y atribución específicas.
