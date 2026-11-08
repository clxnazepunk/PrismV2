# Géneros, moods y cabecera de artistas

## Referencia comprobada

MySpoti inspeccionado en commit95a24e7e4de82ec89d4ec11d5b05b146c44d78e7. SpotifyContent.genres usa searchGenres; ImmersiveSearchBrowse obtiene home.categories; CategoryViewModel muestra playlists de categorías de Spotify. Búsquedas dirigidas de genre/mood no encontraron un clasificador portable de canciones de playlists YTM. Se usó como referencia de experiencia; no se copió código ni se añadió su conexión Spotify. Su licencia raíz es GPL3.

## Prism

MusicTags conserva detalles de géneros iTunes que Prism ya guarda, sin alterar Genres.normalize ni las categorías de radios y recomendaciones. Añade filtros Salsa, Cumbia, Regional mexicana (incluye Banda/Norteño), Balada, Reguetón y Latina cuando sus metadatos lo respalden. Se conserva Rap dentro de Hip-hop. Datos incompletos no garantizan identificación regional.

Los estados de ánimo son etiquetas de canciones de Last.fm: romántico, triste, alegre, tranquilo, enérgico y fiesta. Se muestran separados y pueden combinarse con un género. No se infieren por el género, título o artista. Se necesita una clave de API propia configurada en Ajustes→Recomendaciones y Replay→Moods y géneros, o una clave proporcionada al compilar. No hace falta iniciar sesión para este endpoint. Usar Last.fm para scrobbling no configura por sí solo una clave en esta compilación Dev.
Fuente oficial: https://www.last.fm/api/show/track.getTopTags

Cliente HTTP independiente, sin sesiones YouTube/Spotify. Solo envía artista, título y clave de API. Verifica artista/título de la respuesta, descarta etiquetas débiles y descriptivas. Caché7días en Room existente, sin migración de tablas. Máximo40canciones sin caché por apertura/actualización, solicitudes separadas300ms, salida tras3errores de conexión/servicio o error de API. Actualizar permite continuar con canciones pendientes. En listas grandes puede faltar cobertura y deben permanecer sin mood; no se inventa una clasificación.

La clave opcional se guarda en artist_prefs junto con otras preferencias de artistas y viaja en el respaldo Biblioteca. Los géneros detallados compatibles se pueden seleccionar en grupos de tipografía inteligente; los moods nunca aparecen en ese selector. El género general y el estilo predeterminado siguen siendo alternativa.

## Cabecera

Se elimina el recorte de los huecos100dp/72dp para el logo/firma del perfil. El límite queda en la cabecera completa. La columna usa ModulateAlpha al desplazarse para no crear una máscara pequeña adicional. El editor a pantalla completa reproduce ArtistHeader, la misma cabecera del perfil, con su foto y proporciones. Los desplazamientos verticales nuevos usan la altura total; los guardados antiguos conservan su posición al convertirse. Los pequeños thumbnails del selector siguen acotados para no invadir botones.

No se promete recuperar detalle al ampliar una imagen pequeña. No cambia reproductor, listas guardadas, tipografías nativas ni datos de artistas.
