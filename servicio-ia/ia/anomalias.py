"""
Los cuatro detectores que miran una lectura de agua y dicen si tiene algo raro.

El sistema experto que ya está en el backend compara cada variable contra un umbral fijo que sale
de la tabla parametros_rango. Eso atrapa lo que se sale del rango, y nada más. Este módulo existe
para los casos que un umbral fijo no puede atrapar, ni afinándolo:

  desviación   El estanque tiene su propia costumbre. Uno alimentado por manantial corre a 11 °C
               todo el año; 15 °C ahí es un suceso, y está cómodamente dentro del rango óptimo de
               la trucha. El umbral no se entera. La costumbre del estanque sí.

  tendencia    El oxígeno baja de 8.5 a 6.2 en una semana sin tocar nunca el mínimo crítico de
               5.0. El umbral se enterará el día que lo cruce, que es el día en que ya es tarde.
               La pendiente lo dice con días de anticipación.

  combinación  17 °C es normal y 6.1 mg/L de oxígeno es normal; juntos no, porque el agua tibia
               carga menos oxígeno y la trucha en agua tibia consume más. Ninguna variable sola
               delata la lectura.

  atípica      Lo que ni la combinación atrapa, y que necesita su propia explicación, abajo.


## Por qué hay un modelo y no solo estadística

Los tres primeros detectores son medidas: crecen sin tope cuanto más lejos esté la lectura. El
cuarto es un modelo entrenado (bosque de aislamiento) y hace un trabajo que los otros tres no
pueden hacer, por una razón de forma.

Un estanque con ciclo marcado no produce una nube de lecturas: produce dos grupos, el del día y
el de la noche, con un hueco en medio. Mahalanobis mide contra el centro de la nube, y el centro
de dos grupos cae en el hueco. Una lectura que aterriza donde el estanque nunca aterriza le sale
con la distancia *más baja de todas*: medido en el estanque de prueba, 0.09 contra un corte de
3.04. No es que la medida sea débil ahí; es que está activamente equivocada. El bosque, que no
supone ninguna forma, sí marca ese hueco.

Pero el reparto va en los dos sentidos, y esto se midió:

    puntaje del bosque, historia de 30 lecturas normales
      lectura normal nueva ................................ +0.062
      oxígeno en 4.2 mg/L (a 11 desviaciones) ............. -0.030
      oxígeno en 0.0 mg/L (a 22 desviaciones) ............. -0.030   <-- el mismo
      la lectura normal MÁS solitaria de la historia ....... -0.142   <-- más baja que las dos

El puntaje del bosque se satura: una vez que la lectura cae fuera del rango de los datos, todos
los árboles la aíslan en el primer corte y no hay cómo seguir bajando. Para el bosque, un estanque
sin oxígeno es *menos* raro que su propia lectura normal más solitaria. De modo que:

  - lejos del todo         -> lo ven la desviación y Mahalanobis, que no se saturan
  - dentro pero en un hueco -> solo lo ve el bosque

Cada uno atiende lo suyo. Por eso el bosque se consulta únicamente cuando los otros no encontraron
nada en esa lectura: si ya hay un hallazgo explicable, su aviso débil solo agrega ruido.


## Qué nivel alcanza cada hallazgo

Dos cosas lo deciden, y las dos importan.

La primera es cuánto se puede explicar. Desviación y tendencia llegan a crítica, porque se ponen
en una frase con un número que el cuidador verifica mirando el sensor. El bosque solo llega a
informe: acierta, pero no sabe decir por qué, y no se le puede pedir a alguien que salga de noche
a revisar un estanque por un aviso que nadie puede explicar.

La segunda es que la estadística sola no puede graduar urgencia, y conviene decir por qué, porque
la primera versión de este módulo se equivocó justo ahí. Un estanque de manantial corre a 11 °C
con una variación de décimas, así que una lectura de 12 °C saca un puntaje de 7 desviaciones. Si el
nivel saliera del puntaje, eso sería "crítica" — el mismo grito que el sistema experto reserva para
agua fuera del rango en que la trucha vive. Y la trucha en 12 °C está perfectamente.

De modo que la sorpresa estadística decide *si* hay hallazgo, y la fisiología decide *cuánto*
urge: un valor raro para el estanque pero dentro del rango óptimo llega a advertencia y no más
alto, por grande que sea el puntaje. Para crítica tienen que darse las dos cosas. Es la razón por
la que estos detectores reciben los rangos de parametros_rango y no solo las lecturas.

Nada se inventa. Cuando no hay datos para un detector, ese detector se calla, y la respuesta dice
cuántas lecturas había y cuáles corrieron. Un servicio que opina con cuatro lecturas no sirve.
"""

from __future__ import annotations

from dataclasses import dataclass, replace
from datetime import datetime

import numpy as np
from sklearn.ensemble import IsolationForest

from ia import estadistica as est
from ia.hallazgo import Hallazgo
from ia.tiempo import con_zona

# Las tres variables del agua, en el orden en que el sistema experto las evalúa (Rules.VARIABLES).
VARIABLES = ("temp_c", "ph", "oxigeno_mg_l")

# Cómo se nombra cada una al hablarle al cuidador, y en qué unidad.
NOMBRES = {
    "temp_c": ("La temperatura", "°C"),
    "ph": ("El pH", ""),
    "oxigeno_mg_l": ("El oxígeno", " mg/L"),
}

# Mínimos de cada detector. No son gustos: son el punto donde la medida deja de ser ruido.
#
#   12  Iglewicz y Hoaglin piden al menos 10 valores para que la MAD sirva de referencia.
#   20  Estimar la forma de una nube de tres variables necesita bastante más que las 4 filas que
#       el álgebra exige como mínimo absoluto.
#   40  Del bosque, medido. Con el corte puesto en el puntaje más bajo de la propia historia:
#
#            lecturas   falsas alarmas   atrapa el hueco
#              20            8%               24%
#              40            4%               40%
#              60            8%               56%
#             100            0%               60%
#
#       Debajo de 40 la detección no paga el aviso. De 40 en adelante sí, y sigue mejorando sola
#       a medida que el estanque acumula historia, que es la propiedad que se quería.
MINIMO_DESVIACION = 12
MINIMO_COMBINACION = 20
MINIMO_BOSQUE = 40
MINIMO_TENDENCIA = 12

# Tope de lecturas que se miran. Theil-Sen compara todas las parejas, así que el trabajo crece con
# el cuadrado: 500 lecturas son 125 mil parejas y se resuelven al instante; 5000 serían 12 millones
# y el servicio dejaría esperando a quien lo llamó. Se usan las más recientes, que es lo que importa.
TOPE_LECTURAS = 500

# Hasta cuántos días adelante vale la pena proyectar una tendencia. Más allá, la recta dice más de
# la recta que del estanque.
DIAS_PROYECCION = 7
# Y debajo de cuántos días la cosa pasa de advertencia a crítica.
DIAS_CRITICOS = 2

SEGUNDOS_POR_DIA = 86400.0

# El azar del bosque queda fijo. Sin esto, dos llamadas con los mismos datos pueden dar respuestas
# distintas, y entonces ningún número del documento de la tesis se puede volver a reproducir.
SEMILLA = 0


@dataclass(frozen=True)
class Lectura:
    """Una lectura de agua. Cualquier variable puede faltar: el cuidador mide lo que alcanza."""
    medido_en: datetime
    temp_c: float | None = None
    ph: float | None = None
    oxigeno_mg_l: float | None = None

    def valor(self, variable: str) -> float | None:
        return getattr(self, variable)


@dataclass(frozen=True)
class Rango:
    """
    El umbral del sistema experto, tal como está en la tabla parametros_rango.

    Sirve para dos cosas: saber hacia dónde proyectar una tendencia, y saber si un valor que se
    salió de la costumbre del estanque está además en un lugar donde la trucha lo sufre. Sin eso
    segundo, este servicio no podría graduar la urgencia de nada (ver _desviacion).
    """
    variable: str
    critico_min: float
    critico_max: float | None = None
    optimo_min: float | None = None
    optimo_max: float | None = None

    def fuera_del_optimo(self, valor: float) -> bool | None:
        """None cuando no se sabe: un rango sin los límites óptimos no permite responder."""
        if self.optimo_min is None:
            return None
        if valor < self.optimo_min:
            return True
        return self.optimo_max is not None and valor > self.optimo_max

    def optimo(self) -> str:
        """El rango óptimo escrito para ponerlo en el mensaje."""
        if self.optimo_max is None:
            return f"mínimo {_numero(self.optimo_min)}"
        return f"{_numero(self.optimo_min)}–{_numero(self.optimo_max)}"


@dataclass(frozen=True)
class Analisis:
    lecturas_usadas: int
    dias_cubiertos: float
    hallazgos: list[Hallazgo]
    detectores: list[str]
    """Los que alcanzaron a correr. Vacío significa que no había datos para ninguno."""

    @property
    def suficientes_datos(self) -> bool:
        return bool(self.detectores)


def _numero(valor: float) -> str:
    """Un decimal, y sin el .0 cuando no hace falta: 6.2, 17, 7.5."""
    texto = f"{valor:.1f}"
    return texto[:-2] if texto.endswith(".0") else texto


def _sin_articulo(variable: str) -> str:
    """"La temperatura" -> "temperatura", para poder enumerarlas en una frase."""
    nombre = NOMBRES[variable][0]
    return nombre.split(" ", 1)[1] if " " in nombre else nombre


def _serie(historial: list[Lectura], variable: str) -> tuple[np.ndarray, np.ndarray]:
    """Las horas (en días desde la primera lectura) y los valores, saltando los que faltan."""
    pares = [(l.medido_en, l.valor(variable)) for l in historial if l.valor(variable) is not None]
    if not pares:
        return np.array([]), np.array([])
    origen = pares[0][0]
    dias = np.array([(f - origen).total_seconds() / SEGUNDOS_POR_DIA for f, _ in pares])
    return dias, np.array([float(v) for _, v in pares])


def _nube(historial: list[Lectura]) -> tuple[np.ndarray, tuple[str, ...]]:
    """
    Las lecturas que traen *todas* las variables, como matriz.

    Se exigen completas a propósito. Rellenar un hueco con el promedio de la columna le inventaría
    a esa lectura una combinación que nadie midió, y la combinación es justo lo que aquí se juzga.
    """
    columnas = tuple(v for v in VARIABLES if any(l.valor(v) is not None for l in historial))
    if len(columnas) < 2:
        return np.array([]), ()
    filas = [[l.valor(v) for v in columnas] for l in historial]
    completas = [f for f in filas if all(x is not None for x in f)]
    if not completas:
        return np.array([]), columnas
    return np.array(completas, dtype=float), columnas


def _escala(nube: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    """Centro y dispersión de la nube, para medir la lectura nueva con la misma vara."""
    centro = np.median(nube, axis=0)
    dispersion = np.array([est.mad(nube[:, k]) for k in range(nube.shape[1])])
    dispersion[dispersion == 0.0] = 1.0
    return centro, dispersion


def analizar(historial: list[Lectura], rangos: list[Rango] | None = None) -> Analisis:
    """
    Juzga la última lectura del historial contra las anteriores, y la tendencia de toda la ventana.

    Quien llama decide la ventana; la recomendación es un mes, que es suficiente para fijar la
    costumbre del estanque sin arrastrar el cambio de estación.

    El historial se ordena aquí y no se exige ordenado. Las lecturas llegan de varios celulares que
    sincronizan cuando agarran señal, así que el orden en que entraron a la base no es el orden en
    que se midieron, y todo este módulo razona sobre el tiempo: cuál es "la actual", cuántos días
    cubre la ventana, hacia dónde va la pendiente.
    """
    rangos_por_variable = {r.variable: r for r in (rangos or [])}
    if len(historial) < 2:
        return Analisis(len(historial), 0.0, [], [])

    historial = sorted((replace(l, medido_en=con_zona(l.medido_en)) for l in historial),
                       key=lambda l: l.medido_en)[-TOPE_LECTURAS:]
    actual = historial[-1]
    anteriores = historial[:-1]
    dias_cubiertos = (historial[-1].medido_en - historial[0].medido_en).total_seconds() / SEGUNDOS_POR_DIA

    hallazgos: list[Hallazgo] = []
    detectores: list[str] = []

    for detector in (_desviacion, _tendencia, _combinacion):
        nombre, encontrados = detector(actual, anteriores, historial, rangos_por_variable)
        if nombre is not None:
            detectores.append(nombre)
            hallazgos.extend(encontrados)

    # El bosque solo opina si los demás no encontraron nada *en esta lectura*. Una tendencia no lo
    # calla: habla de la ventana, no de la lectura, y la lectura puede estar en un hueco igual.
    sobre_la_lectura = [h for h in hallazgos if h.clase in ("desviacion", "combinacion")]
    if not sobre_la_lectura:
        nombre, encontrados = _atipica(actual, anteriores, historial, rangos_por_variable)
        if nombre is not None:
            detectores.append(nombre)
            hallazgos.extend(encontrados)

    return Analisis(len(historial), round(dias_cubiertos, 2), hallazgos, detectores)


def _desviacion(actual, anteriores, historial, rangos):
    """Cada variable contra la costumbre de su propio estanque."""
    corrio = False
    hallazgos = []
    for variable in VARIABLES:
        valor = actual.valor(variable)
        if valor is None:
            continue
        _, previos = _serie(anteriores, variable)
        if previos.size < MINIMO_DESVIACION:
            continue
        puntaje = est.puntaje_robusto(float(valor), previos)
        if puntaje is None:
            # Variable sin variación en el historial: no hay con qué medir si se salió
            continue
        corrio = True
        if abs(puntaje) < est.CORTE_DESVIACION:
            continue

        nombre, unidad = NOMBRES[variable]
        direccion = "encima" if puntaje > 0 else "debajo"
        costumbre = est.mediana(previos)
        rango = rangos.get(variable)
        fuera = rango.fuera_del_optimo(float(valor)) if rango is not None else None

        frase = (f"{nombre} está en {_numero(float(valor))}{unidad}, muy por {direccion} de lo "
                 f"normal de este estanque ({_numero(costumbre)}{unidad}).")
        if fuera:
            # Se salió de su costumbre Y está donde la trucha lo sufre: las dos cosas a la vez
            nivel = "critica"
            frase += f" Y además quedó fuera del rango óptimo de la trucha ({rango.optimo()}{unidad})."
        else:
            # Raro para el estanque, pero el pez está bien. La urgencia la pone la fisiología, no
            # la estadística: en un estanque de manantial muy estable, un grado de más saca un
            # puntaje enorme sin que eso signifique que haya que salir corriendo de noche.
            nivel = "advertencia"
            frase += (" Sigue en un rango que la trucha tolera, pero es un cambio brusco para este"
                      " estanque y vale la pena buscarle la causa.")

        hallazgos.append(Hallazgo(
            clase="desviacion",
            nivel=nivel,
            variable=variable,
            puntaje=round(abs(puntaje), 2),
            medido_en=actual.medido_en,
            mensaje=frase,
        ))
    return ("desviacion" if corrio else None), hallazgos


def _tendencia(actual, anteriores, historial, rangos):
    """Hacia dónde va cada variable, y cuándo llegaría al umbral crítico si sigue así."""
    corrio = False
    hallazgos = []
    for variable in VARIABLES:
        dias, valores = _serie(historial, variable)
        if valores.size < MINIMO_TENDENCIA:
            continue
        # El mínimo de días no es un gusto: medido, en una ventana de un día el ciclo del día
        # inventa tendencias con más acuerdo que las tendencias de verdad (ver estadistica.py)
        if dias[-1] - dias[0] < est.DIAS_MINIMOS_TENDENCIA:
            continue
        resultado = est.pendiente_theil_sen(dias, valores)
        if resultado is None:
            continue
        corrio = True
        por_dia, acuerdo = resultado
        if acuerdo < est.ACUERDO_MINIMO or por_dia == 0.0:
            continue

        nombre, unidad = NOMBRES[variable]
        rumbo = "subiendo" if por_dia > 0 else "bajando"
        frase = (f"{nombre} viene {rumbo} {_numero(abs(por_dia))}{unidad} por día "
                 f"({_numero(dias[-1] - dias[0])} días de datos)")

        rango = rangos.get(variable)
        limite = None
        if rango is not None:
            limite = rango.critico_max if por_dia > 0 else rango.critico_min

        faltan = None
        if limite is not None:
            faltan = (limite - float(valores[-1])) / por_dia

        # Sin umbral hacia dónde ir, o con el umbral ya cruzado (de eso avisa el sistema experto),
        # o todavía lejos: la pendiente es información útil pero no urgente.
        if faltan is None or faltan <= 0 or faltan > DIAS_PROYECCION:
            hallazgos.append(Hallazgo(clase="tendencia", nivel="informe", variable=variable,
                                      puntaje=round(acuerdo, 2), medido_en=actual.medido_en,
                                      mensaje=frase + "."))
            continue

        cuando = "mañana" if faltan < 1.5 else f"en {round(faltan)} días"
        hallazgos.append(Hallazgo(
            clase="tendencia",
            nivel="critica" if faltan <= DIAS_CRITICOS else "advertencia",
            variable=variable,
            puntaje=round(acuerdo, 2),
            medido_en=actual.medido_en,
            mensaje=(f"{frase}. A este ritmo llega al límite crítico de "
                     f"{_numero(limite)}{unidad} {cuando}."),
        ))
    return ("tendencia" if corrio else None), hallazgos


def _combinacion(actual, anteriores, historial, rangos):
    """La lectura entera contra la forma de la nube de lecturas anteriores."""
    nube, columnas = _nube(anteriores)
    if nube.size == 0 or nube.shape[0] < MINIMO_COMBINACION:
        return None, []
    if any(actual.valor(v) is None for v in columnas):
        # Lectura incompleta: no se puede juzgar una combinación que no se midió completa
        return None, []

    punto = np.array([float(actual.valor(v)) for v in columnas])
    distancia = est.mahalanobis(punto, nube)
    if distancia is None:
        return None, []

    if distancia <= est.corte_mahalanobis(len(columnas)):
        return "combinacion", []

    partes = [f"{_sin_articulo(v)} {_numero(float(actual.valor(v)))}{NOMBRES[v][1]}" for v in columnas]
    return "combinacion", [Hallazgo(
        clase="combinacion",
        nivel="advertencia",
        variable=None,
        puntaje=round(distancia, 2),
        medido_en=actual.medido_en,
        mensaje=("Esta combinación no se había visto en el estanque: "
                 + ", ".join(partes)
                 + ". Cada valor por separado puede estar en rango; juntos, no se parecen a nada "
                   "de lo medido antes."),
    )]


def _atipica(actual, anteriores, historial, rangos):
    """
    El bosque de aislamiento, para los huecos de adentro.

    El corte es el puntaje más bajo que sacó cualquier lectura real del estanque. Dicho en palabras,
    el aviso significa: "esta lectura es más rara que cualquiera de las N que se midieron antes".
    Es un criterio estricto, se explica en una frase, y deja las falsas alarmas por debajo del 8%
    (medido; ver MINIMO_BOSQUE).

    El corte propio del modelo (puntaje negativo) no sirve aquí: con él, una de cada tres lecturas
    normales queda marcada, porque contamination='auto' está calibrado para que buena parte de los
    datos de entrenamiento caigan del lado malo.
    """
    nube, columnas = _nube(anteriores)
    if nube.size == 0 or nube.shape[0] < MINIMO_BOSQUE:
        return None, []
    if any(actual.valor(v) is None for v in columnas):
        return None, []

    # La lectura actual se escala con las mismas cifras que la nube. Si se recalcularan
    # incluyéndola, ayudaría a definir el normal del que se la quiere separar.
    centro, dispersion = _escala(nube)
    entrenamiento = (nube - centro) / dispersion

    bosque = IsolationForest(random_state=SEMILLA, contamination="auto")
    bosque.fit(entrenamiento)
    corte = float(bosque.decision_function(entrenamiento).min())

    punto = np.array([[float(actual.valor(v)) for v in columnas]])
    puntaje = float(bosque.decision_function((punto - centro) / dispersion)[0])
    if puntaje >= corte:
        return "atipica", []

    return "atipica", [Hallazgo(
        clase="atipica",
        # Solo informe: el bosque acierta pero no sabe decir por qué.
        nivel="informe",
        variable=None,
        puntaje=round(corte - puntaje, 4),
        medido_en=actual.medido_en,
        mensaje=(f"El modelo marcó esta lectura como más rara que cualquiera de las "
                 f"{nube.shape[0]} anteriores del estanque, sin que ninguna variable sola se "
                 f"salga de lo normal. Vale la pena confirmarla con una segunda medición."),
    )]
