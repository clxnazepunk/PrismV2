# Fondo y modos de mezcla de logo/firma

Ajustar muestra vista previa sobre la cabecera real. Cada recurso conserva su opción de quitar fondo uniforme, tolerancia y modo normal/multiplicar/trama/oscurecer/aclarar; las opciones no soportadas por el Android del dispositivo se desactivan. Esos ajustes son independientes del tamaño, posición y del otro recurso.

La eliminación del fondo ocurre localmente mediante Coil3.6.3 Transformation (API verificada en su fuente oficial). Detecta color dominante del borde y solo recorre píxeles conectados a ese borde, suavizando su franja. Mantiene los detalles claros encerrados. Si el borde es inconsistente o ya transparente, evita adivinar un sujeto. No es segmentación de IA y puede dejar fondos complejos o huecos interiores. La imagen original se mantiene sin modificar; desactivar la casilla o restablecer vuelve a la presentación original.

Procesamiento como máximo1024px por lado, en Dispatcher.Default, con comprobación de cancelación y caché de memoria por fuente/tolerancia. El deslizador aplica la tolerancia al soltarlo para evitar trabajo continuo. No añade modelos, dependencias, permisos, subidas ni solicitudes de red. La resolución del resultado procesado puede ser menor que el original.

Los modos de mezcla son otra alternativa: oscurecer/multiplicar disimulan fondos blancos; trama/aclarar ayudan con fondos negros. No equivalen a un PNG transparente exportado. Se aplican a la capa de la imagen sobre la foto del perfil. Los parámetros se guardan en artist_identity/index.json y viajan en el respaldo Biblioteca.

Referencias oficiales: https://github.com/coil-kt/coil/blob/3.6.3/coil-core/src/commonMain/kotlin/coil3/transform/Transformation.kt y https://developer.android.com/reference/kotlin/androidx/compose/ui/graphics/BlendMode .

## Descargar recursos

Cada logo/firma tiene Descargar original y Exportar PNG con ajustes de fondo. El original conserva sus bytes, transparencia y formato PNG/JPEG/WebP/SVG. El PNG rasteriza hasta2048px y aplica la eliminación de fondo guardada si está activada, conservando transparencia. Inversión de color previa ya está en el recurso local. Escala/posición y mezcla con la foto son presentación del perfil y no se hornean en una imagen sin esa foto. La eliminación de fondo limita su resultado a1024px.

Se elige destino mediante ACTION_CREATE_DOCUMENT con MIME y nombre por lanzamiento; no se añade permiso de almacenamiento general. La operación usa el recurso que se seleccionó, no otro artista que aparezca después. No sube archivos salvo que el propio usuario elija un proveedor de documentos remoto. Guardar en un proveedor real todavía necesita prueba en dispositivo.
