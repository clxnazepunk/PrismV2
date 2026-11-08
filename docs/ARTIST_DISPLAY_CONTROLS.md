# Controles de identidad del artista

- Predeterminado: solo logo; si no existe o no se decodifica, firma; finalmente texto nativo.
- Elección por artista de cualquier combinación no vacía de Logo, Firma y Texto. Si los recursos elegidos faltan, permanece el texto como alternativa.
- Nombre debajo de la firma opcional por artista. Si se elige Texto también, ese texto evita duplicar el nombre.
- Ajustar por recurso: escala 0,25×–12×, desplazamiento horizontal y vertical, vista previa, restablecer y guardar. La escala no depende de la resolución original y no mejora su nitidez.
- Invertir color crea un PNG local con RGB invertido y transparencia conservada. No se envían imágenes a terceros. El origen y licencia permanecen; la versión remota no se borra.
- Elecciones, posición y archivos están en artist_identity, incluido en Biblioteca de la copia de seguridad existente. Las instalaciones antiguas reciben valores predeterminados.
- Foto del perfil: el selector y su trabajo viven fuera del menú; se copia el resultado al artista que abrió el selector aunque cambie la selección de artista mientras tanto.
- Se conservan tipografías, favoritos, reproducción y demás funciones de Prism.

Verificación: ArtistDisplayTest (prioridad, combinaciones, límites, inversión, persistencia y compatibilidad anterior) y ArtistPhotoPickerTest (resultado retrasado después de cerrar menú, copia real y persistencia). Se incluyen en Actions junto a los controles anteriores. Compilar no sustituye probar presentación y selector con proveedores reales en un teléfono.
