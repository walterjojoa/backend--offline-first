# Servicio de IA

Lo que el sistema experto de umbrales no puede ver.

El backend ya tiene un sistema experto: compara cada lectura de agua contra los umbrales de la
tabla `parametros_rango` y genera alertas cuando algo se sale. Eso funciona y seguirá
funcionando. Este servicio existe para los casos en que un umbral fijo, por bien afinado que
esté, no puede ayudar:

| El caso | Por qué el umbral no lo ve |
|---|---|
| El estanque de manantial corre a 11 °C y hoy marcó 15 °C | 15 °C está dentro del rango óptimo de la trucha (10–16). El umbral no tiene nada que decir. |
| El oxígeno baja de 8.5 a 6.2 en una semana | Nunca cruzó el mínimo crítico de 5.0. El umbral avisará el día que lo cruce, que es el día en que ya es tarde. |
| Agua tibia con oxígeno justo | Cada valor está en rango. Juntos no: el agua tibia carga menos oxígeno y la trucha en agua tibia consume más. |
| El lote crece la mitad de lo que el agua permite | No es un problema de agua, así que ningún umbral de agua lo detecta. Es ración, densidad o sanidad. |

## Cómo correrlo

```bash
python -m venv .venv
.venv/Scripts/pip install -r requirements-dev.txt   # en Linux o Mac: .venv/bin/pip
.venv/Scripts/python -m pytest                      # 90 pruebas
.venv/Scripts/python -m uvicorn ia.api:app --reload
```

Queda en `http://127.0.0.1:8000`, con la documentación de las rutas en `/docs`.

Sin `API_KEY` en el entorno el servicio queda abierto, que está bien en la máquina de uno y avisa
al arrancar. En el servidor la pone Render (ver `render.yaml` en la raíz del repositorio).

## Las tres rutas

```
GET  /salud              si está vivo y qué detectores sabe correr
POST /analizar/agua      una lectura contra el historial de su estanque
POST /proyectar/lote     peso proyectado, fecha de cosecha y supervivencia
```

No tiene base de datos ni guarda nada. Todo lo que necesita para responder llega en el cuerpo de
la petición. El backend Java es el dueño de los datos y este servicio es una calculadora a la que
se le pasan los números: se puede apagar, reiniciar o volver a desplegar sin que nada se pierda.

### Ejemplo

```bash
curl -X POST http://127.0.0.1:8000/analizar/agua \
  -H 'Content-Type: application/json' \
  -d '{"estanque_id":"11111111-1111-4111-8111-111111111111",
       "historial":[{"registrado_en":"2026-10-01T06:00:00Z","temp_c":11.0}, ...],
       "rangos":[{"variable":"temp_c","critico_min":6,"critico_max":18,
                  "optimo_min":10,"optimo_max":16}]}'
```

Los nombres de los campos son los mismos de las columnas de la base y de los eventos que manda el
celular (`registrado_en`, `oxigeno_mg_l`, `cantidad_inicial`). El backend arma estos cuerpos
copiando lo que ya tiene, sin una traducción en el medio donde se pierdan campos en silencio.

## Qué hay adentro

```
ia/estadistica.py   las medidas robustas: mediana, MAD, Theil-Sen, Mahalanobis
ia/anomalias.py     los cuatro detectores de agua
ia/crecimiento.py   la proyección del lote
ia/esquemas.py      el contrato HTTP
ia/api.py           las rutas
```

Cada módulo arranca con un comentario largo que explica por qué está hecho así. Vale leerlos: lo
que está escrito ahí es lo que sostiene las decisiones de la tesis.

### Los cuatro detectores de agua

**desviación** · Cada variable contra la costumbre de su propio estanque, con mediana y MAD en vez
de promedio y desviación estándar. La razón es concreta: cuando el cuidador saca el sensor del agua
quedan lecturas de aire guardadas, y con promedios **dos** de esas lecturas alcanzan para mover el
"normal" del estanque lo suficiente para que una caída real de oxígeno pase desapercibida. Está
medido en `pruebas/test_estadistica.py`.

**tendencia** · Hacia dónde va cada variable, por el método de Theil-Sen, y cuándo llegaría al
umbral crítico. Theil-Sen y no la recta de mínimos cuadrados: con una sola lectura mala al final de
la serie, los mínimos cuadrados llegan a decir que el oxígeno **sube** mientras en realidad baja
0.3 mg/L por día. La ventana tiene que cubrir al menos tres días, y ese mínimo salió de medirlo —
en una ventana de un día el ciclo del sol inventa tendencias con más acuerdo que las tendencias de
verdad.

**combinación** · La lectura entera contra la forma de la nube de lecturas anteriores (distancia de
Mahalanobis). Atrapa lo que ninguna variable sola delata.

**atípica** · Un bosque de aislamiento, que es la única parte que es un modelo entrenado. Hace un
trabajo que los otros tres no pueden: un estanque con ciclo marcado no produce una nube sino **dos
grupos**, el del día y el de la noche, con un hueco en medio. Mahalanobis mide contra el centro de
la nube, y el centro de dos grupos cae en el hueco, así que a una lectura que aterriza donde el
estanque nunca aterriza le sale la distancia *más baja de todas* — medido: 0.09 contra un corte de
3.04. No es que la medida sea débil ahí; es que está activamente equivocada.

Pero el reparto va en los dos sentidos. El puntaje del bosque se **satura**: una vez que la lectura
cae fuera del rango de los datos, todos los árboles la aíslan en el primer corte y no hay cómo
seguir bajando. Para el bosque, un estanque con oxígeno en cero es *menos* raro que su propia
lectura normal más solitaria. De modo que cada uno atiende lo suyo: lo que está lejos del todo lo
ven la desviación y Mahalanobis, que no se saturan; lo que está dentro pero en un hueco solo lo ve
el bosque. Por eso el bosque se consulta únicamente cuando los otros no encontraron nada.

### Lo que la proyección del lote no es

El crecimiento **no** es un modelo aprendido, y conviene decirlo así en la sustentación. Es el
coeficiente térmico de crecimiento (Iwama y Tautz, 1981), que es el estándar en piscicultura y
descansa en un hecho de la trucha: no crece con el tiempo, crece con el calor acumulado del agua.

Lo que sí hace el servicio es **ajustarle el coeficiente con los datos de la finca**. El valor de
la literatura sirve para arrancar; a la segunda biometría el modelo ya está hablando del estanque
real. La Cocha está a 2.760 metros y el agua es fría, así que es de esperar que el coeficiente
ajustado salga por debajo del de referencia, y que eso se pueda medir es en sí un resultado.

## Lo que este servicio no sabe, y lo dice

Tres cosas, y están puestas a propósito porque callarlas sería peor:

**No opina sin datos.** Cada detector tiene un mínimo de lecturas por debajo del cual se calla, y
la respuesta trae `detectores: []` y `suficientes_datos: false`. Eso **no** es lo mismo que "revisé
y está todo bien", y el backend tiene que poder distinguirlos. Por eso tampoco sale como error: un
400 se registraría como "la IA falló", y no falló — no tenía con qué responder.

**La supervivencia es un techo, no una medición.** Se calcula con las mortalidades *registradas*, y
un alevino que muere y se descompone nunca se registra. La medición de verdad es el contador de
alevinos: comparar lo que entrega al cosechar contra lo que se sembró es lo único que cierra la
cuenta. Que el sistema tenga las dos cosas y se puedan contrastar es justo lo que la tesis mide.

**Lo que no se puede calcular sale en `notas`, no como cero.** Un peso proyectado de 0 g y un peso
proyectado desconocido no son lo mismo, y quien lea la pantalla tiene que notar la diferencia.

Y una cuarta, que es la más importante de todas para la sustentación: **no hay un histórico real de
La Cocha todavía**. Los detectores de anomalías no lo necesitan, porque aprenden del propio
estanque sin etiquetas y mejoran solos a medida que se acumulan lecturas. Los umbrales de
`parametros_rango` y el coeficiente de referencia sí son valores sin citar, con la misma advertencia
que ya trae la base de datos: hay que ajustarlos con el productor antes del piloto.

## El servicio se puede caer, y no pasa nada

La IA **agrega, no sostiene**. Si este servicio está apagado, dormido o roto, el backend sigue
registrando, sincronizando y generando sus alertas de umbral igual que antes. Es una decisión de
diseño, no una consecuencia: el sistema tiene que funcionar en un estanque sin señal, y un servicio
que sólo vive en la nube no puede ser parte del camino crítico.

En el plan gratuito de Render el servicio se **duerme** tras un rato sin uso y tarda cerca de un
minuto en despertar. Por eso el backend nunca lo llama dentro de una sincronización: la llamada va
por fuera, y cuando la respuesta llega se publica como evento para que la pantalla la reciba sola.
Ese es el papel del manejador de eventos.
