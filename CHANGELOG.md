# Cambios

## 0.3.0

### Cambios
- Código Java traducido al inglés: paquetes (`controller`, `model`, `repository`, `service`), clases,
  métodos, variables, comentarios y pruebas.
- La API no cambia: rutas, parámetros, campos del JSON, tablas y mensajes siguen en español
  (`@JsonProperty` en los DTO y `@Column` en las entidades).
- Los errores de validación siguen nombrando los campos como en el JSON (`fecha_siembra`).

## 0.2.0

### Nuevo
- `GET /api/estanques/{id}` y `GET /api/lotes/{id}`.
- Historiales del lote: conteos, mortalidad, alimentación y biometrías.
- Resumen diario de temperatura y pH (`/lecturas/diario`) para las gráficas del panel.
- Descarga de lecturas en CSV (`/lecturas.csv`).
- Resumen del lote con días de cultivo, densidad (kg/m³) y conversión alimenticia.
- Hora en que se atiende cada alerta (`atendida_en`).
- Versión desplegada en `/salud` y en `/docs`.
- GitHub Actions corre las pruebas en cada push.

### Cambios
- Las lecturas repetidas fuera de rango no crean otra alerta si ya hay una igual pendiente.
- Se rechazan fechas anteriores a 2024 (reloj del dispositivo sin configurar).
- Se rechazan fechas de siembra en el futuro y `dispositivo_id` con caracteres raros.
- Los errores de validación nombran los campos en snake_case, igual que el JSON.
- CORS limitado a los métodos y encabezados que usa el panel.
- Respuestas comprimidas, fechas en UTC y pool de conexiones ajustado a Neon.
- Contenedor Docker sin usuario root.

### Corregido
- Crear un estanque o lote con un id existente devuelve 409 en vez de 500.
- Rutas y métodos inexistentes responden en JSON.
- Las horas del servidor se recortan a microsegundos, como las guarda PostgreSQL.

## 0.1.0
- Primera versión: sincronización offline-first (push/pull), catálogo, consultas y alertas.
