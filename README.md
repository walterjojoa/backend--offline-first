# La Cocha API (Java)

Backend offline-first del proyecto de grado: recibe desde la app del cuidador las lecturas de
pH y temperatura, los conteos de alevinos, la mortalidad, la alimentación y las biometrías;
genera alertas y calcula el resumen de cada lote.

**Stack:** Java 17 · Spring Boot 3.5 · Spring Data JPA · Flyway · PostgreSQL (Neon) · Docker en Render

## Estructura

```
src/main/java/com/lacocha/backend/
├── config/        Base de datos (Neon), clave X-API-Key, CORS y Swagger
├── model/         Entidades JPA (tablas)
├── repository/    Consultas a la base de datos (Spring Data)
├── dto/           Lo que entra y sale de la API (records)
├── service/       Lógica: sincronización, resumen del lote y reglas (sistema experto)
│                  PisciculturaFacade es la única puerta que usan los controladores
└── controller/    Rutas REST y manejo de errores
src/main/resources/db/
├── migration/     Tablas (Flyway), comunes a PostgreSQL y H2
├── motor/         Migraciones que cambian según el motor (postgresql/ y h2/)
└── semilla/       Datos de ejemplo para la sustentación (Flyway no los aplica)
```

### Base de datos

El esquema lo diseña el repo [base-de-datos-first-offline](https://github.com/camilin0811/base-de-datos-first-offline)
(Andrés Camilo Muñoz) y es la **única fuente** de las migraciones: este backend las copia con el mismo
número. Para cambiar una tabla:

1. Se agrega la migración nueva en el repo base-de-datos (nunca se edita una que ya corrió).
2. Se copia aquí con el mismo nombre y se adapta el código Java si hace falta.

Lo más importante que exige la base:

- **Dispositivos:** cada evento apunta a un `dispositivo_id` registrado. El primer push lo registra solo.
- **Usuarios y sesiones (V24):** el celular es el aparato y el usuario es la persona. En la finca se
  prestan los celulares, así que son cosas distintas y la sesión apunta a las dos.
- **Nombres únicos:** no se repite el nombre de un estanque ni el código de un lote dentro del mismo estanque.
- **Lote cerrado** = tiene `fecha_cierre`, y no puede ser anterior a la siembra.
- **Umbrales y curva de alimentación** viven en tablas (`parametros_rango`, `tasas_alimentacion`,
  `factores_temperatura`), cada número con su fuente.

Para probar las migraciones en PostgreSQL de verdad (además de H2): `docker compose up -d` y
`.\mvnw spring-boot:run -Dspring-boot.run.profiles=postgres`.

### Patrones de diseño

`SyncService.push()` hacía cinco trabajos en 143 líneas: registrar el dispositivo, fusionar estanques,
fusionar lotes, guardar los eventos y escribir la bitácora. Los cinco patrones están puestos en esas
costuras, no de adorno. Hoy ese método tiene 24 líneas.

| Patrón | Dónde | Qué problema resuelve |
|---|---|---|
| **Facade** | `service/PisciculturaFacade.java` | Los cinco controladores inyectaban cinco servicios distintos. Ahora inyectan uno. Un controlador solo tiene que saber su ruta, su JSON y qué caso de uso llamar; en qué servicio vive ese caso de uso, y si mañana se mueve, no es asunto suyo. |
| **Adapter** | `service/PushJsonAdapter.java` | El celular y el ESP32 suben JSON sin tipo: una trama del contador llega como un objeto cuyo campo `tipo` es la única pista de qué es. Este es el único lugar que la convierte en un record ya validado, y que nombra el campo que falló como lo nombra el JSON y no como lo nombra Java. Es el gemelo en el servidor del `ArduinoSerialAdapter` de la app. |
| **Factory Method** | `service/EventIngestors.java` | Antes había un `instanceof WaterReadingInput` enterrado en el push para decidir quién dispara alertas. Ahora cada subclase de `Factory` responde por un `tipo` del protocolo y decide quién lo atiende. Aceptar un evento nuevo es agregar una subclase y registrarla. Es además la **única** lista de las etiquetas del protocolo: `Events.TYPES` desapareció. |
| **Bridge** | `service/EventStore.java` + `JpaEventStore.java` | `EventIngestion` tiene las reglas de qué se acepta, qué es duplicado y qué se rechaza, y sostiene un `EventStore` sin saber nunca cuál es. Nunca ve un `EntityManager`. Eso es lo que permite probar esas reglas sin base de datos (ver *Pruebas automáticas*). |
| **Decorator** | `service/EventStoreDecorators.java` | Dos capacidades transversales que se envuelven desde fuera y se apilan. `SourceTally` cuenta lo guardado por `origen` y lo deja en el log (`events from cel-cuidador-1: contador=120, manual=3`): la tesis compara el contador contra el conteo a mano, y eso lo responde sin una consulta. `Trace` escribe una línea por evento y **solo se envuelve si su nivel está en DEBUG**, porque un push trae hasta 500. |

Los decoradores se construyen por cada push, no una vez: un conteo por `origen` que sumara dos pushes
distintos no diría nada.

### Idioma del código

El código Java (clases, métodos, variables y comentarios) está en **inglés**. Lo que ve el usuario sigue
en **español**: las rutas (`/api/estanques`), los campos del JSON (`registrado_en`), las tablas de la
base de datos y los mensajes de error y de alertas. Así no cambia nada para la app ni para el ESP32.

Los nombres del JSON se fijan con `@JsonProperty` en los DTO y las columnas con `@Column` en las entidades.

| Español (API / BD) | Inglés (código) |
|---|---|
| estanque | `Pond` |
| lote | `Batch` |
| lectura de agua | `WaterReading` |
| conteo de alevinos | `FryCount` |
| mortalidad | `Mortality` |
| alimentación | `Feeding` |
| biometría | `Biometry` |
| alerta | `Alert` |
| reglas (sistema experto) | `Rules` |
| dispositivo | `Device` |
| sincronización (bitácora) | `SyncLog` |

## Correr en local

No hace falta Neon: en local usa una base H2 en la carpeta `data/`.

```powershell
.\mvnw spring-boot:run
```

- Documentación interactiva: http://localhost:8080/docs → botón **Authorize** → `cambia-esta-clave`
- Pruebas automáticas: `.\mvnw test`

También se puede abrir la carpeta en IntelliJ o VS Code y ejecutar `LaCochaApplication`
con el perfil `local` (`--spring.profiles.active=local`).

## Cómo funciona la sincronización

Todo lo que registra el celular es un **evento** con un `id` (UUID) que genera el propio celular.
Los eventos no se editan, por eso no hay conflictos entre dispositivos.

`POST /api/sync/push` (encabezado `X-API-Key`)

```json
{
  "dispositivo_id": "cel-cuidador-1",
  "estanques": [{"id": "…", "nombre": "Tanque 1", "tipo": "tanque", "actualizado_en": "2026-10-08T14:00:00Z"}],
  "lotes":     [{"id": "…", "estanque_id": "…", "codigo": "L12", "cantidad_inicial": 5000, "actualizado_en": "…"}],
  "eventos": [
    {"tipo": "lectura_agua", "id": "…", "estanque_id": "…", "temp_c": 14.2, "ph": 7.05, "mv": 1510, "origen": "sensor", "registrado_en": "…"},
    {"tipo": "conteo",       "id": "…", "lote_id": "…", "total": 5200, "cortes_multiples": 37, "origen": "contador", "registrado_en": "…"},
    {"tipo": "mortalidad",   "id": "…", "lote_id": "…", "cantidad": 3, "origen": "voz", "registrado_en": "…"},
    {"tipo": "alimentacion", "id": "…", "lote_id": "…", "kg": 0.5, "registrado_en": "…"},
    {"tipo": "biometria",    "id": "…", "lote_id": "…", "peso_promedio_g": 2.5, "muestra": 50, "registrado_en": "…"}
  ]
}
```

Las fechas van en formato ISO con zona horaria (`Z` o `-05:00`).

Respuesta: `aceptados`, `duplicados`, `obsoletos`, `rechazados` (con el error) y `alertas_generadas`.
El celular borra de su cola todo lo que salga en cualquiera de esas listas.

- **Reintentos seguros:** si la señal se cae y el celular reenvía, lo repetido sale en `duplicados` y no se guarda dos veces.
- **Un evento malo no frena la cola:** se rechaza solo ese y los demás se guardan.
- **Estanques y lotes** sí se editan: gana el `actualizado_en` más reciente; el cambio viejo sale en `obsoletos`.
- **Máximo** 500 eventos por envío.
- **Fechas:** se rechazan las que están más de 1 día en el futuro o antes de 2024 (reloj sin configurar).
- **Dispositivo dado de baja:** su push completo recibe 403.
- **Nombre de estanque o código de lote repetido:** se rechaza solo ese, con el motivo.
- **Alertas:** si ya hay una alerta pendiente igual (mismo estanque, variable y nivel), una lectura repetida no crea otra.

`GET /api/sync/pull?desde=<servidor_en anterior>` devuelve estanques y lotes cambiados desde
el último pull y las alertas pendientes. El cursor es la hora del servidor, no la del celular,
para que un celular con el reloj atrasado no se pierda cambios.

## Otros endpoints

| Método | Ruta | Para qué |
|---|---|---|
| GET/POST | `/api/estanques` | Listar / crear estanques |
| GET/PATCH | `/api/estanques/{id}` | Ver / editar estanque |
| GET/POST | `/api/lotes` | Listar (filtros `estanque_id`, `estado`) / crear lotes |
| GET/PATCH | `/api/lotes/{id}` | Ver / editar lote |
| GET | `/api/estanques/{id}/lecturas` | Historial de pH y temperatura (`desde`, `hasta`, `limite`) |
| GET | `/api/estanques/{id}/lecturas/diario` | Mínimo, máximo y promedio por día (`dias`, por defecto 7) |
| GET | `/api/estanques/{id}/lecturas.csv` | Descargar las lecturas en CSV (`desde`, `hasta`) |
| GET | `/api/lotes/{id}/resumen` | Días de cultivo, población, supervivencia, biomasa, densidad, ración y conversión alimenticia |
| GET | `/api/lotes/{id}/conteos` | Historial de conteos (`limite`) |
| GET | `/api/lotes/{id}/mortalidades` | Historial de mortalidad (`limite`) |
| GET | `/api/lotes/{id}/alimentaciones` | Historial de alimentación (`limite`) |
| GET | `/api/lotes/{id}/biometrias` | Historial de biometrías (`limite`) |
| GET | `/api/alertas` | Alertas (`pendientes`, `estanque_id`) |
| POST | `/api/alertas/{id}/atender` | Marcar alerta como atendida (guarda `atendida_en` y `atendida_por`; opcional `dispositivo_id`) |
| GET | `/api/parametros` | Umbrales de agua y curva de alimentación con su fuente |
| GET | `/api/dispositivos` | Celulares y nodos que sincronizan, con su último envío |
| PATCH | `/api/dispositivos/{id}` | Ponerle nombre (`descripcion`) o darlo de baja (`activo: false`) |
| GET | `/api/sincronizaciones` | Bitácora de cada push (`dispositivo_id` opcional) |
| GET | `/salud` | Chequeo de Render y versión desplegada (sin clave) |

Todos los errores salen como `{"detalle": "..."}` con los nombres de campo igual que en el JSON.

### Resumen del lote

- **Población:** último conteo menos las muertes registradas después de ese conteo.
- **Densidad (kg/m³):** biomasa entre el volumen del estanque (`volumen_m3`).
- **Conversión alimenticia (FCA):** kg de alimento total entre kg de biomasa ganada desde la siembra.
- **Días de cultivo:** desde `fecha_siembra` hasta hoy (hora de Colombia).

Los umbrales y la tabla de alimentación están en la base (ver `GET /api/parametros`). Son **valores de
referencia**: hay que ajustarlos con el productor y citarlos de AUNAP/FAO y del fabricante del alimento.
Cambiarlos es un `UPDATE` en la base y reiniciar el servicio, no hace falta volver a compilar.

## Desplegar en Render + Neon

Render no tiene Java nativo, por eso se despliega con el `Dockerfile` incluido.

1. En **Neon** crea un proyecto `lacocha` y copia la cadena de conexión (`postgresql://…`).
   Se puede pegar tal cual: el backend la convierte a JDBC.
2. Sube esta carpeta a un repositorio de GitHub.
3. En **Render** → **New** → **Blueprint** → elige el repositorio. Render lee `render.yaml`.
4. Cuando pida las variables, llena:
   - `DATABASE_URL` = la cadena de Neon
   - `API_KEY` no se escribe: Render genera una clave larga al azar (luego se copia en la app y el panel)
   - `ALLOWED_ORIGINS` = la URL del panel web, o `http://localhost:5173` por ahora
5. **Apply**. La primera compilación tarda unos minutos. Al terminar abre
   `https://<tu-servicio>.onrender.com/salud` → debe decir `{"estado":"ok","version":"…"}`.

Las tablas se crean solas al arrancar (Flyway, carpetas `db/migration` y `db/motor/postgresql`).

Nota: en el plan gratis Render apaga el servicio tras 15 min sin uso y el primer envío tarda
~1 minuto en despertarlo. La app debe tener un tiempo de espera largo en el primer intento.

## Pruebas automáticas

`.\mvnw test` corre las pruebas de la API (con H2 en memoria) y las unitarias de las reglas,
las fechas, la configuración y la ingestión de eventos. GitHub Actions las corre en cada push a `main`
(pestaña **Actions** del repositorio).

`EventIngestionTest` prueba las reglas del push **sin base de datos y sin contexto de Spring**: el
`EventStore` es un mapa. Eso es lo que compra el Bridge. Cubre que un conteo se acepta con su
dispositivo y su hora de llegada, que el mismo evento dos veces vuelve como duplicado y se guarda una
sola vez, que un evento huérfano se rechaza solo y el resto de la cola pasa, que el error nombra el
campo como el JSON, que sin `origen` se guarda como `manual`, que una lectura fuera de rango levanta
alerta y una idéntica no la repite, y que un conteo nunca levanta alertas. Tarda milisegundos; el mismo
terreno por HTTP en `ApiTest` cuesta levantar la aplicación y el esquema H2.
