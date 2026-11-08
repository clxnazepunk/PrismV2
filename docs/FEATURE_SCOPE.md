# Alcance acordado de PrismV2

Actualizado el 8 de noviembre de 2026. Prism es el producto principal.
Todas sus funciones actuales se conservan. Esta lista expresa trabajo solicitado,
no funcionalidades ya entregadas ni garantía de compatibilidad.

## Funciones propias
| ID estable | Estado | Alcance |
| --- | --- | --- |
| N01 / F01 | Pendiente | Elegir manualmente la calidad/resolución disponible de miniaturas, conservando proporciones y recorte cuadrado. |
| N02 / F03 | Pendiente | Portadas animadas locales elegibles por álbum; admitir formatos viables tras verificarlos, sin prometer universalidad. |
| N03 / F04 | Prioridad máxima, pendiente | Logo y firma independientes, importables/editables, con recurso local y API configurable. Revisar el código de firmas de BitChord en GitHub antes de adaptarlo. |
| N04 / F04 | Pendiente | Guardar perfiles con logo/firma para uso offline y mostrarlos cuando el artista tenga canciones o álbumes descargados. Área Catálogo si es la alternativa adecuada. |
| N05 / F04 | Pendiente | Respaldo/restauración de recursos y asociaciones. La actualización remota no reemplaza una elección local. |
| N06 / F05 | Verificar y ampliar | Subidos de YouTube Music: canciones, álbumes y artistas; aprovechar lo existente en Prism. |
| N07 / F06 | Pendiente | Reunir álbumes oficiales guardados y privados subidos del artista. Primero evaluar el perfil actual; usar Catálogo si la integración es compleja/frágil. |
| N08 / F07 | Pendiente | Orden y agrupación por año de lanzamientos oficiales/no oficiales disponibles, con fechas desconocidas explícitas. |
| N09 / F07 | Pendiente | Búsqueda conjunta por artista y cronología, en perfil o Catálogo según la evaluación N07. |
| N10 / F09 | Primera etapa local en revisión; remota pendiente | Recomendaciones relacionadas al final de playlist y acción inmediata de añadir, inspiradas en MySpoti. |
| N11 / F10 | Desmarcada por el propietario | No activar una playlist radio fija de 30 canciones sin nueva indicación. |
| N12 / F10 | Pendiente | Radio sucesiva/radio de la radio para descubrir música; no garantizar rareza ni cantidad exacta. |

## Referencias de BitChord seleccionadas
| ID | Decisión vigente |
| --- | --- |
| B01 | Corregida: Prism ya tiene portadas oficiales animadas. Evaluar Canvas adicional y elección para el espacio 1:1 entre Canvas y portada animada; no duplicar el apartado. |
| B02 | Mantener: API configurable de firmas, integrada en N03. |
| B03 | Retirada del trabajo nuevo por redundancia; conservar cualquier función existente de miniaturas cuadradas. |
| B04 | Mantener integración Spotify de listas/favoritos emparejados; no es reproducción nativa Spotify como MySpoti. Comparar antes con la integración existente. |
| B05 | Retirada del trabajo nuevo por redundancia; conservar crossfade de Prism. |
| B06 | Retirada del trabajo nuevo por redundancia; conservar DJ/mezclas de Prism. |
| B07 | Mantener calidad de audio separada para Wi-Fi/datos móviles. |
| B08 | Tarjetas para compartir letras con interfaz cuidada y personalizable, aprovechando antes lo que Prism tenga. |
| B09 | Retirada del trabajo nuevo por redundancia; conservar importaciones/integraciones existentes de Prism. |

## MySpoti
M01: experiencia de radio y recomendaciones como referencia para N10/N12.
No implica migrar su motor Spotify ni copiar credenciales o protocolos.

## Funciones de Prism que deben permanecer
- P01: interfaz Apple Music, colores de portada y cristal.
- P02: personalización de fuentes/iconos/fondos/distribución y Looks compartibles.
- P03: portadas estáticas y fotos personales de artistas.
- P04: reproducción/biblioteca/listas YouTube Music.
- P05: AutoEq, nivelación, velocidad, tono y omitir silencios.
- P06: Sing.
- P07: letras por palabra, traducción, romanización, estilos, inmersión y búsqueda por frase.
- P08: vídeos musicales.
- P09: géneros, mezclas locales y novedades.
- P10: uploads y cronología existentes.
- P11: importación de historial y Replay.
- P12: descargas, predicción, actualización de listas y música/carpetas locales.
- P13: podcasts y reconocimiento de canciones.
- P14: control por navegador/traslado local, Last.fm y ahorro de recursos.
Esta enumeración conserva las selecciones; NO autoriza eliminar otras funciones no listadas.

## Primer bloque de trabajo
La preparación inicial añade CI y convenciones sin modificar el producto.
Después, N03/N05: adaptar primero el almacenamiento y selección independiente
de logo/firma y su respaldo, integrados con la UI existente. N04/N07/N09
necesitan comprobar identidad de artistas y álbumes privados antes de cerrar
su ubicación. Un PR de preparación no entrega esas funciones.

## Procedencia
- Base del fork: 1abbed19d374b84e6cda4e1c63db937d1f70befd (Prism 1.8.2.1).
- Referencia BitChord: kushagrasinghx/BitChord, ArtistSignatures.kt.
- Referencia MySpoti: kota-zo/myspoti, recomendaciones de ContextScreen.
- Revisar GPLv3 y licencias de cada componente antes de transferir código.
