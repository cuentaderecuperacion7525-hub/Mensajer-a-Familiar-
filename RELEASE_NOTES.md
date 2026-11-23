# Novedades de FamiChat

## 2.8.51
- Conexión completa a la nueva base de datos de producción (`famichat-prod`) sin candado diario de escrituras (`RESOURCE_EXHAUSTED`), conservando todos los perfiles, chats, mensajes, notas, agenda y compras existentes.
- Actualización de reglas y disparadores de notificaciones en segundo plano hacia `famichat-prod`.

## 2.8.50
- Los dispositivos que ya estaban registrados en la familia ahora pueden cargar y mostrar los perfiles en modo solo lectura incluso si la base de datos alcanzó su límite diario de escrituras (`RESOURCE_EXHAUSTED`).
- Verificación directa al servidor al consultar las familias del dispositivo para evitar falsos vacíos por caché local.

## 2.8.49
- Reducción drástica del consumo diario de escrituras en Firestore (~80–90% menos): se limitó y agrupó en lotes la resincronización histórica al unirse nuevos dispositivos, se eliminó la sincronización repetida en el selector de perfiles y se espació el latido de estado «En línea» a cada 60 segundos.
- Diagnóstico más preciso cuando la base de datos de Firestore tiene activo el candado «Free Tier» (límite de 20,000 escrituras/día incluso con plan Blaze).

## 2.8.48
- Corrección definitiva del error «Tiempo de espera agotado al unirse a la familia» en instalaciones nuevas o al reinstalar: ahora se confirma correctamente la unión directa al servidor y se cargan de inmediato todos los perfiles creados por el administrador.
- Reducción importante de escrituras redundantes en la nube al abrir la app desde dispositivos ya conectados, protegiendo el límite diario gratuito de Firebase.
- Mensajes de diagnóstico más claros si el servidor reporta algún límite o bloqueo específico al descargar los perfiles familiares.

## 2.8.47
- Carga mucho más rápida de perfiles en instalaciones nuevas (para familiares que instalan por primera vez): se une directo a la familia y descarga los perfiles de inmediato en lugar de esperar escrituras previas o consultas vacías.
- Se corrigió el reinicio doble del observador de perfiles que podía dejar girando «Cargando perfiles...» durante demasiado tiempo.
- Si ocurre algún problema de red o de servidor al cargar perfiles, ahora se muestra el motivo exacto junto al botón «Reintentar» en lugar de girar sin aviso.

## 2.8.46
- Corrección urgente del cierre al abrir detectado en la 2.8.45 (se desactivó el recorte agresivo de R8 que eliminaba clases internas de DataStore/Firestore).
- Se conserva la compilación Release optimizada (`debuggable=false` con ART AOT y perfiles de arranque) y la misma firma para actualizar encima sin desinstalar.

## 2.8.45
- Compilación optimizada de lanzamiento (Release con R8 y optimización nativa de Android) manteniendo la misma firma para actualizar encima sin desinstalar.
- Reducción importante del tamaño de la app y del tiempo de arranque en frío al abrir desde el icono.

## 2.8.44
- Transición única y limpia al abrir FamiChat: se eliminó el doble destello del logo entre la pantalla de inicio de Android y los Chats.
- Restauración inmediata de sesión también en la primera apertura tras actualizar, sin mostrar una segunda pantalla de carga.

## 2.8.43
- Arranque en frío mucho más rápido: optimización del tamaño de la app (R8) para acelerar la apertura inicial desde el icono.
- Restauración inmediata de la sesión y el perfil guardado al abrir, entrando a Chats desde el primer cuadro sin esperar lecturas secundarias.
- Procesamiento de la lista de chats fuera del hilo principal y carga diferida de secciones secundarias para que el inicio se sienta instantáneo.

## 2.8.42
- Pantalla de inicio oficial de Android con el logo de FamiChat al abrir la app para evitar parpadeos o pantallas vacías en el arranque.
- Carga inmediata del tema y tamaño de letra guardados antes de dibujar el primer cuadro.
- Nueva línea de tiempo local del último arranque en Ajustes → Diagnóstico (solo administrador) para verificar los tiempos y eventos de inicio sin consultas adicionales.

## 2.8.41
- Rediseño completo de Ajustes → Actualizaciones con la tarjeta limpia de «Buscar actualizaciones» y «Novedades de esta versión», y «Detalles técnicos» con etiqueta Admin solo para el administrador.
- Nuevos cuadros emergentes limpios para «Estás al día», «Hay una versión nueva» con sección «Qué incluye», «Buscando…», «Descargando», «Lista para instalar», permiso de instalación y errores.
- Caché de 10 minutos al buscar actualizaciones manualmente para evitar consultas repetidas innecesarias y límite de espera de 10 segundos.

## 2.8.40
- Nueva opción "Compartir FamiChat" en Ajustes para enviar el archivo de la app con un nombre limpio (FamiChat-2.8.40.apk) sin texto técnico del paquete.

## 2.8.39
- Ajustes → Actualizaciones abre directamente la pantalla renovada con el estado de la versión, tarjeta "Qué incluye", historial bajo demanda, botón "Actualizar ahora" y opción "Más tarde" por 24 horas.
- "Novedades de esta versión" vuelve a estar disponible directamente en Ajustes.

## 2.8.38
- Pantalla Actualizaciones ajustada exactamente con la tarjeta limpia de dos opciones: "Buscar actualizaciones" y "Novedades de esta versión", sin textos adicionales afuera.
- Al tocar "Buscar actualizaciones" se abre la pantalla completa de verificación, novedades de la nueva versión y descarga.

## 2.8.37
- Se restauró la vista directa de Actualizaciones con la versión instalada, el estado y las opciones "Buscar actualizaciones" y "Novedades de esta versión" en una sola pantalla.

## 2.8.36
- Actualizaciones ahora abre un menú limpio con solo "Buscar actualizaciones" y "Novedades de esta versión", sin leyendas adicionales afuera.
- Al tocar "Buscar actualizaciones" se abre la pantalla dedicada con el estado de la versión, novedades de la nueva actualización y descarga.

## 2.8.35
- "Novedades de esta versión" ahora vive dentro de Actualizaciones junto a "Buscar actualizaciones" en una tarjeta agrupada limpia, dejando Ajustes más ordenado.

## 2.8.34
- Pantalla de Actualizaciones renovada con resumen claro en "Qué incluye" y enlace al historial de versiones bajo demanda.
- Nuevo botón de píldora "Actualizar ahora" y opción "Más tarde" que pospone el aviso por 24 horas manteniendo el indicador en Ajustes.
- Mejor presentación de los estados de búsqueda, descarga, instalación y errores en modo claro, oscuro y letra grande.

## 2.8.33
- Notificaciones instantáneas desde el servidor sin el aviso fijo "FamiChat activo" para ahorrar batería como en las apps de mensajería comerciales.
- Se evitaron avisos duplicados cuando ya tienes FamiChat abierto en pantalla.
- Nueva opción en Ajustes → Notificaciones por si algún teléfono sin servicios de Google necesita activar la conexión permanente.

## 2.8.32
- La barra de descarga de actualizaciones ahora llega suavemente al 100% y muestra "Descarga completa" antes de iniciar la instalación.

## 2.8.31
- Nuevo botón estilo píldora en Actualizaciones con azul vibrante, respuesta táctil suave y estado integrado "Buscando..." con indicador de carga.

## 2.8.30
- Novedades de esta versión ahora muestra los cambios reales de cada actualización al instante y sin internet.
- Barra de progreso en vivo al descargar nuevas versiones desde Actualizaciones.
- Tema oscuro completo en Mi perfil y avisos de confirmación inmediatos en la misma pantalla donde guardas tus cambios.
- Menú superior de tres puntos y pantalla de Ajustes renovados con apariencia adaptable y tamaño de letra.
