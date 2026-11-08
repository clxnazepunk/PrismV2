# Tipografías de artistas

Ajustes → Personalizar Prism → Texto → Tipografías de artistas.

Predeterminado: tipografía fija estándar, sin asignar letras decorativas automáticamente. Se puede elegir otra fuente fija, una aleatoria por visita, tipografía inteligente por grupos o el modo original de Prism. Las elecciones manuales por artista prevalecen, y los logos/firmas de imagen siguen independientes.

Los grupos admiten varios géneros y una fuente existente de Prism. Editar o eliminar un grupo libera sus géneros; los de otros grupos se muestran desactivados. La persistencia también elimina duplicados, incluso al restaurar preferencias. Sin género reconocido o grupo asignado se usa la fuente predeterminada. No se infiere un género a partir del nombre del artista en esta función.

## Límite confirmado del clasificador existente

data/meta/MetaRepository.kt, Genres.all: 16 categorías amplias. artistGenre obtiene estimaciones de Deezer e iTunes y sus cachés. Rap se normaliza a Hip-hop; Salsa, Cumbia, Banda y Norteño actualmente a Pop. No se han añadido subgéneros ficticios ni modificado los filtros o recomendaciones existentes. La tipografía inteligente comparte estas categorías con playlists; su precisión depende de los metadatos existentes.

ArtistPrefs guarda la configuración junto a las elecciones de artistas en artist_prefs. Viaja en el apartado Biblioteca del respaldo existente. La importación de ajustes dañados recupera valores predeterminados sin borrar selecciones individuales.

ArtistTypographyConfigTest cubre prioridad de modos, grupos múltiples, duplicados, liberación de géneros, datos ausentes, fuentes desconocidas, aleatoriedad estable por visita y recarga de preferencias. Las comprobaciones no demuestran presentación real en cada teléfono ni exactitud del género de cada artista.
