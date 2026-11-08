# Respaldo de perfiles y personalizaciones

Al crear una copia, incluir Biblioteca. La descripción de esa opción y el editor de
logos/firmas lo indican. No es necesario incluir las cuentas para llevar estos recursos.

Se respalda el perfil guardado, sus asociaciones por identificador, logos y firmas
manuales/automáticos, y las portadas personalizadas existentes. Los recursos nuevos
guardan nombres relativos para que no dependan de la carpeta de la instalación original.
Una restauración de sólo Ajustes conserva las personalizaciones actuales de Biblioteca.

Compatibilidad: las portadas antiguas que apuntaban a archivos privados absolutos se
reubican al abrir sus preferencias, únicamente cuando pertenecían a files/covers y
el archivo está presente en la carpeta restaurada. No se cambian enlaces web ni
imágenes de ubicaciones externas. No se inventan archivos que la copia no contenga.

No cambia el formato general de copia ni sustituye la biblioteca. Mantiene el
comportamiento existente: restaurar Biblioteca reemplaza esa parte con la de la copia.
Cerrar sesión sigue limpiando los perfiles offline conforme a Prism.

Pruebas: restauración real mediante Backup.applyPending de un perfil junto con sus
dos imágenes, prioridad local tras una actualización posterior, conservación al restaurar
sólo Ajustes y reubicación de portadas antiguas. Las pruebas usan la estructura preparada
por Backup.stage; no afirman haber probado la selección de archivos ni el ZIP completo
en un teléfono. Esa comprobación y la instalación siguen pendientes de dispositivo.
