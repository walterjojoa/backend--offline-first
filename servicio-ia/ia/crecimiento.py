"""
Hacia dónde va un lote: cuánto va a pesar, cuándo llega al peso de venta, y cuántos van a quedar.

A diferencia de los detectores de anomalias.py, esto no es un modelo aprendido: es un modelo de
crecimiento de la literatura, al que se le ajusta un parámetro con los datos de la finca. Vale la
pena decirlo así y no llamarlo "IA" sin más, porque en la sustentación la diferencia importa.


## El modelo

Se usa el coeficiente térmico de crecimiento (Iwama y Tautz, 1981), que es el estándar en
piscicultura y descansa en un hecho de la trucha: no crece con el tiempo, crece con el *calor*
acumulado. Un mes a 14 °C vale más que un mes a 9 °C, y la cuenta lo dice:

    W2^(1/3) = W1^(1/3) + (TGC / 1000) * T * dias

con el peso en gramos y T en grados. Despejando TGC se obtiene el coeficiente de *esta* finca a
partir de dos biometrías y la temperatura que hubo entre ellas. Eso es lo que lo vuelve útil aquí:
el número de la literatura sirve para arrancar, y a la segunda biometría el modelo ya está hablando
del estanque real y no de una trucha promedio de otro país.

La Cocha está a 2.760 metros y el agua es fría, así que es de esperar que el coeficiente ajustado
salga por debajo del de referencia. Que eso se pueda medir es, en sí, un resultado.


## De dónde sale cada número, y qué no se puede saber

La supervivencia se calcula con las mortalidades *registradas*, y eso tiene un límite que hay que
dejar escrito: un alevino que muere y se descompone, o que se lo come un ave, nunca se registra.
De modo que la supervivencia que sale de aquí es un **techo**, no una medición. La medición de
verdad es el contador de alevinos: comparar lo que el contador entrega al cosechar contra la
cantidad sembrada es lo único que cierra la cuenta. Que el sistema tenga las dos cosas y se puedan
contrastar es justamente lo que la tesis quiere mostrar, y por eso el techo se reporta como techo
en vez de disfrazarse de dato.

Cuando falta con qué calcular algo, no se calcula y queda dicho en 'notas'. La proyección de un
lote sin biometrías ni temperatura sería un número inventado con cara de pronóstico.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date, datetime, timedelta

import numpy as np

from ia import estadistica as est
from ia.hallazgo import Hallazgo

SEGUNDOS_POR_DIA = 86400.0

# Coeficiente de referencia de la trucha arcoíris. Se deja con la misma advertencia que el proyecto
# ya usa en parametros_rango, porque es igual de cierta: es un valor de referencia sin citar, que
# hay que ajustar con el productor y citar de AUNAP/FAO antes del piloto. Solo manda hasta que el
# lote tenga dos biometrías; a partir de ahí gobierna el ajustado.
TGC_REFERENCIA = 2.0
FUENTE_TGC = ("Valor de referencia sin citar: se reemplaza por el ajustado en cuanto el lote "
              "tenga dos biometrías")

# Para ajustar el coeficiente hacen falta dos biometrías separadas por algo de tiempo. Menos de una
# semana y la diferencia de peso es más error de muestreo que crecimiento: pesar treinta alevinos
# de un lote de diez mil tiene su propia dispersión, y en cinco días el crecimiento no la supera.
DIAS_MINIMOS_ENTRE_BIOMETRIAS = 7.0

# Debajo de qué fracción del coeficiente de referencia se considera que el lote crece mal. No es un
# valor medido: es un criterio, y queda nombrado para que el productor lo pueda mover cuando tenga
# varios ciclos con los cuales compararlo.
FRACCION_CRECIMIENTO_POBRE = 0.6

# Para decir que la mortalidad se aceleró hacen falta las dos cosas: que la tasa reciente supere
# varias veces la anterior, y que en números absolutos sea algo y no dos peces. Sin lo segundo,
# pasar de una muerte a tres es una "triplicación" que no significa nada.
FACTOR_MORTALIDAD_ACELERADA = 2.0
MINIMO_MUERTES_RECIENTES = 5

# "Reciente" es una semana fija, y no una fracción del tiempo que lleva el lote. La primera versión
# comparaba el último tercio contra los dos primeros, y eso diluye justo el caso que importa: un
# brote mata durante cuatro o cinco días, y en un lote de dos meses el último tercio son veinte
# días, de los cuales quince están tranquilos. Medido sobre el mismo lote de prueba, el tercio daba
# 2.6 veces la tasa anterior (advertencia) y la semana da 9.5 (crítica), que es lo que de verdad
# está pasando. Una semana es además la unidad con la que se revisa un estanque.
DIAS_VENTANA_RECIENTE = 7.0
# Y hace falta algo de historia antes de la ventana para tener con qué comparar.
DIAS_MINIMOS_ANTERIORES = 7.0

# La supervivencia que se espera de un lote de alevinos bien llevado. Mismo estatus que el TGC de
# referencia: orienta, no valida.
SUPERVIVENCIA_ESPERADA_PCT = 80.0


@dataclass(frozen=True)
class Biometria:
    """Un pesaje de muestra. 'muestra' es cuántos peces se pesaron, cuando quedó registrado."""
    medido_en: datetime
    peso_promedio_g: float
    muestra: int | None = None


@dataclass(frozen=True)
class Mortalidad:
    medido_en: datetime
    cantidad: int


@dataclass(frozen=True)
class Temperatura:
    medido_en: datetime
    temp_c: float


@dataclass(frozen=True)
class Lote:
    sembrado_en: date | None = None
    cantidad_inicial: int | None = None
    peso_inicial_g: float | None = None


@dataclass(frozen=True)
class Proyeccion:
    dias_adelante: int
    # --- crecimiento ---
    tgc: float | None = None
    tgc_ajustado: bool = False
    fuente_tgc: str | None = None
    temp_media_c: float | None = None
    peso_actual_g: float | None = None
    peso_proyectado_g: float | None = None
    dias_para_objetivo: int | None = None
    fecha_objetivo: date | None = None
    # --- supervivencia (techo, no medición: ver el encabezado del módulo) ---
    cantidad_actual: int | None = None
    cantidad_proyectada: int | None = None
    supervivencia_pct: float | None = None
    muertes_por_dia: float | None = None
    # --- lo que se encontró y lo que no se pudo calcular ---
    hallazgos: list[Hallazgo] = field(default_factory=list)
    notas: list[str] = field(default_factory=list)


def _dias(desde: datetime, hasta: datetime) -> float:
    return (hasta - desde).total_seconds() / SEGUNDOS_POR_DIA


def _numero(valor: float) -> str:
    texto = f"{valor:.1f}"
    return texto[:-2] if texto.endswith(".0") else texto


def _temp_media(temperaturas: list[Temperatura], desde: datetime, hasta: datetime) -> float | None:
    """
    La temperatura media del tramo, por mediana.

    Mediana y no promedio por lo mismo de siempre: una lectura tomada con el sensor fuera del agua
    corre el promedio de un tramo corto varios grados, y aquí la temperatura multiplica directamente
    al crecimiento proyectado.
    """
    dentro = [t.temp_c for t in temperaturas if desde <= t.medido_en <= hasta]
    if not dentro:
        return None
    return float(np.median(dentro))


def _ajustar_tgc(biometrias: list[Biometria],
                 temperaturas: list[Temperatura]) -> tuple[float | None, int]:
    """
    El coeficiente de esta finca, de las biometrías que tenga.

    Con más de dos se calcula el de cada par consecutivo y se toma la mediana, que es lo que
    impide que un pesaje con una muestra mala defina el crecimiento de todo el lote.

    Devuelve (coeficiente, cuántos pares se pudieron usar).
    """
    coeficientes = []
    for antes, despues in zip(biometrias, biometrias[1:]):
        dias = _dias(antes.medido_en, despues.medido_en)
        if dias < DIAS_MINIMOS_ENTRE_BIOMETRIAS:
            continue
        if antes.peso_promedio_g <= 0 or despues.peso_promedio_g <= 0:
            continue
        temperatura = _temp_media(temperaturas, antes.medido_en, despues.medido_en)
        if temperatura is None or temperatura <= 0:
            continue
        crecimiento = despues.peso_promedio_g ** (1 / 3) - antes.peso_promedio_g ** (1 / 3)
        coeficientes.append(1000.0 * crecimiento / (temperatura * dias))

    if not coeficientes:
        return None, 0
    return float(np.median(coeficientes)), len(coeficientes)


def _peso_a(peso_g: float, tgc: float, temp_c: float, dias: float) -> float:
    """El peso después de tantos días a esa temperatura. Nunca por debajo de cero."""
    raiz = peso_g ** (1 / 3) + (tgc / 1000.0) * temp_c * dias
    return max(raiz, 0.0) ** 3


def _dias_hasta(peso_g: float, objetivo_g: float, tgc: float, temp_c: float) -> float | None:
    """Cuántos días faltan para el peso objetivo. None si ya lo pasó o si no va a llegar."""
    if objetivo_g <= peso_g:
        return 0.0
    if tgc <= 0 or temp_c <= 0:
        return None
    falta = objetivo_g ** (1 / 3) - peso_g ** (1 / 3)
    return 1000.0 * falta / (tgc * temp_c)


def proyectar(lote: Lote,
              biometrias: list[Biometria] | None = None,
              mortalidades: list[Mortalidad] | None = None,
              temperaturas: list[Temperatura] | None = None,
              dias_adelante: int = 30,
              peso_objetivo_g: float | None = None,
              hoy: datetime | None = None) -> Proyeccion:
    """
    Qué se puede decir de este lote con lo que tiene registrado.

    Las listas llegan ordenadas de lo más viejo a lo más nuevo. 'hoy' se puede fijar para que las
    pruebas no dependan del día en que corran.
    """
    biometrias = sorted(biometrias or [], key=lambda b: b.medido_en)
    mortalidades = sorted(mortalidades or [], key=lambda m: m.medido_en)
    temperaturas = sorted(temperaturas or [], key=lambda t: t.medido_en)
    ahora = hoy or datetime.now(tz=(temperaturas[0].medido_en.tzinfo if temperaturas else None))

    hallazgos: list[Hallazgo] = []
    notas: list[str] = []

    crecimiento = _proyectar_crecimiento(lote, biometrias, temperaturas, dias_adelante,
                                         peso_objetivo_g, ahora, hallazgos, notas)
    supervivencia = _proyectar_supervivencia(lote, mortalidades, dias_adelante, ahora,
                                            hallazgos, notas)

    return Proyeccion(dias_adelante=dias_adelante, hallazgos=hallazgos, notas=notas,
                      **crecimiento, **supervivencia)


def _proyectar_crecimiento(lote, biometrias, temperaturas, dias_adelante, objetivo,
                           ahora, hallazgos, notas) -> dict:
    vacio = {"tgc": None, "tgc_ajustado": False, "fuente_tgc": None, "temp_media_c": None,
             "peso_actual_g": None, "peso_proyectado_g": None,
             "dias_para_objetivo": None, "fecha_objetivo": None}

    # El peso de partida: la última biometría si hay, y si no el de la siembra
    if biometrias:
        peso_actual = biometrias[-1].peso_promedio_g
        desde = biometrias[-1].medido_en
    elif lote.peso_inicial_g and lote.sembrado_en:
        peso_actual = lote.peso_inicial_g
        desde = datetime.combine(lote.sembrado_en, datetime.min.time(), tzinfo=ahora.tzinfo)
    else:
        notas.append("Sin biometrías ni peso de siembra no hay de dónde partir para proyectar el "
                     "crecimiento. Basta con registrar un pesaje de muestra.")
        return vacio

    if peso_actual <= 0:
        notas.append("El peso registrado no es positivo, así que no se puede proyectar.")
        return vacio

    temperatura = _temp_media(temperaturas, desde, ahora)
    if temperatura is None:
        temperatura = _temp_media(temperaturas, datetime.min.replace(tzinfo=ahora.tzinfo), ahora)
    if temperatura is None:
        notas.append("Sin lecturas de temperatura no se puede proyectar el crecimiento: la trucha "
                     "crece con el calor acumulado del agua, no con los días que pasan.")
        return vacio

    tgc, pares = _ajustar_tgc(biometrias, temperaturas)
    ajustado = tgc is not None
    if not ajustado:
        tgc = TGC_REFERENCIA
        if len(biometrias) < 2:
            notas.append("El coeficiente de crecimiento es el de referencia, no el de esta finca. "
                         "Con una segunda biometría pasa a calcularse con los datos del lote.")
        else:
            notas.append(f"Las biometrías están demasiado juntas (hacen falta al menos "
                         f"{_numero(DIAS_MINIMOS_ENTRE_BIOMETRIAS)} días entre dos) o les falta "
                         f"temperatura en el tramo, así que se usa el coeficiente de referencia.")

    # Un coeficiente ajustado negativo significa que el lote está perdiendo peso. Proyectar con él
    # daría un peso que baja hasta cero, y eso no es un pronóstico sino una extrapolación absurda.
    if ajustado and tgc <= 0:
        hallazgos.append(Hallazgo(
            clase="crecimiento", nivel="critica", puntaje=round(abs(tgc), 3), medido_en=ahora,
            mensaje=(f"El lote no está creciendo: entre las dos últimas biometrías el peso promedio "
                     f"bajó. Con el agua a {_numero(temperatura)} °C debería estar subiendo. "
                     f"Revisar alimentación, densidad y sanidad antes de seguir."),
        ))
        notas.append("No se proyecta peso hacia adelante: con el lote perdiendo peso, la cuenta "
                     "daría un número sin sentido.")
        return {**vacio, "tgc": round(tgc, 3), "tgc_ajustado": True, "fuente_tgc": "ajustado",
                "temp_media_c": round(temperatura, 2), "peso_actual_g": round(peso_actual, 1)}

    if ajustado and tgc < TGC_REFERENCIA * FRACCION_CRECIMIENTO_POBRE:
        hallazgos.append(Hallazgo(
            clase="crecimiento", nivel="advertencia", puntaje=round(tgc / TGC_REFERENCIA, 2),
            medido_en=ahora,
            mensaje=(f"El lote crece a {round(100 * tgc / TGC_REFERENCIA)}% de lo que el agua a "
                     f"{_numero(temperatura)} °C permitiría. La temperatura no es el problema: "
                     f"mirar ración, densidad del estanque y sanidad."),
        ))

    dias_objetivo = None
    fecha_objetivo = None
    if objetivo:
        faltan = _dias_hasta(peso_actual, objetivo, tgc, temperatura)
        if faltan is not None:
            dias_objetivo = int(round(faltan))
            # Desde los días ya redondeados, no desde el valor con decimales: si no, la fecha y el
            # número de días que se muestran juntos pueden quedar diciendo cosas distintas
            fecha_objetivo = (ahora + timedelta(days=dias_objetivo)).date()

    return {
        "tgc": round(tgc, 3),
        "tgc_ajustado": ajustado,
        "fuente_tgc": f"ajustado con {pares} par(es) de biometrías" if ajustado else FUENTE_TGC,
        "temp_media_c": round(temperatura, 2),
        "peso_actual_g": round(peso_actual, 1),
        "peso_proyectado_g": round(_peso_a(peso_actual, tgc, temperatura, dias_adelante), 1),
        "dias_para_objetivo": dias_objetivo,
        "fecha_objetivo": fecha_objetivo,
    }


def _proyectar_supervivencia(lote, mortalidades, dias_adelante, ahora, hallazgos, notas) -> dict:
    vacio = {"cantidad_actual": None, "cantidad_proyectada": None,
             "supervivencia_pct": None, "muertes_por_dia": None}

    if not lote.cantidad_inicial or lote.cantidad_inicial <= 0:
        notas.append("Sin la cantidad sembrada no se puede calcular supervivencia.")
        return vacio
    if not lote.sembrado_en:
        notas.append("Sin la fecha de siembra no se puede calcular una tasa de mortalidad por día.")
        return vacio

    muertes = sum(m.cantidad for m in mortalidades)
    vivos = max(lote.cantidad_inicial - muertes, 0)
    sembrado = datetime.combine(lote.sembrado_en, datetime.min.time(), tzinfo=ahora.tzinfo)
    dias_transcurridos = max(_dias(sembrado, ahora), 1.0)
    por_dia = muertes / dias_transcurridos

    if muertes > lote.cantidad_inicial:
        # No es un error de cálculo: es una inconsistencia en los datos, y hay que decirlo
        notas.append(f"Las mortalidades registradas ({muertes}) pasan la cantidad sembrada "
                     f"({lote.cantidad_inicial}). Hay registros de más o una siembra mal anotada.")

    supervivencia = 100.0 * vivos / lote.cantidad_inicial
    if supervivencia < SUPERVIVENCIA_ESPERADA_PCT:
        hallazgos.append(Hallazgo(
            clase="mortalidad", nivel="advertencia", puntaje=round(supervivencia, 1), medido_en=ahora,
            mensaje=(f"Van {muertes} muertes registradas de {lote.cantidad_inicial} sembrados: "
                     f"{_numero(supervivencia)}% de supervivencia, por debajo del "
                     f"{_numero(SUPERVIVENCIA_ESPERADA_PCT)}% que se espera de un lote bien llevado. "
                     f"Y es un techo: lo que no se alcanzó a registrar no está contado."),
        ))

    _mirar_aceleracion(mortalidades, sembrado, ahora, hallazgos)

    return {
        "cantidad_actual": vivos,
        "cantidad_proyectada": max(vivos - int(round(por_dia * dias_adelante)), 0),
        "supervivencia_pct": round(supervivencia, 1),
        "muertes_por_dia": round(por_dia, 2),
    }


def _mirar_aceleracion(mortalidades, sembrado, ahora, hallazgos) -> None:
    """
    Si el lote se está muriendo más rápido esta semana que antes.

    Es la pregunta que el total acumulado no responde: un lote puede llevar una supervivencia
    aceptable y estar en plena mortandad ahora mismo. Se compara la última semana contra todo lo
    anterior (ver DIAS_VENTANA_RECIENTE para por qué una semana fija y no una fracción).
    """
    if not mortalidades:
        return
    total_dias = _dias(sembrado, ahora)
    if total_dias < DIAS_VENTANA_RECIENTE + DIAS_MINIMOS_ANTERIORES:
        # Lote demasiado nuevo: no hay un "antes" contra el cual comparar la semana
        return

    corte = ahora - timedelta(days=DIAS_VENTANA_RECIENTE)
    antes = sum(m.cantidad for m in mortalidades if m.medido_en < corte)
    recientes = sum(m.cantidad for m in mortalidades if m.medido_en >= corte)
    if recientes < MINIMO_MUERTES_RECIENTES:
        return

    dias_antes = _dias(sembrado, corte)
    dias_recientes = DIAS_VENTANA_RECIENTE
    tasa_antes = antes / dias_antes
    tasa_reciente = recientes / dias_recientes

    if tasa_antes == 0:
        # Empezó a morirse ahora, sin antecedente: eso es lo más urgente que puede pasar
        hallazgos.append(Hallazgo(
            clase="mortalidad", nivel="critica", puntaje=round(tasa_reciente, 2), medido_en=ahora,
            mensaje=(f"El lote no tenía mortalidad registrada y en los últimos "
                     f"{round(dias_recientes)} días van {recientes} muertes "
                     f"({_numero(tasa_reciente)} por día). Empezó algo."),
        ))
        return

    veces = tasa_reciente / tasa_antes
    if veces < FACTOR_MORTALIDAD_ACELERADA:
        return
    hallazgos.append(Hallazgo(
        clase="mortalidad",
        nivel="critica" if veces >= 2 * FACTOR_MORTALIDAD_ACELERADA else "advertencia",
        puntaje=round(veces, 2), medido_en=ahora,
        mensaje=(f"La mortalidad se aceleró: {_numero(tasa_reciente)} muertes por día en los "
                 f"últimos {round(dias_recientes)} días, contra {_numero(tasa_antes)} por día "
                 f"antes. Son {_numero(veces)} veces más."),
    ))
