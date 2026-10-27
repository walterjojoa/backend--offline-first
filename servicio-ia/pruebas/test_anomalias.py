"""
Cada prueba monta un estanque con una historia concreta y comprueba que el detector dice de él lo
que el módulo promete. Varias comprueban además que el sistema experto de umbrales fijos *no* lo
habría dicho: eso es lo que justifica que este servicio exista.
"""

from datetime import datetime, timedelta, timezone

import numpy as np
import pytest

from ia import anomalias as an
from ia import estadistica as est

INICIO = datetime(2026, 10, 1, 6, 0, tzinfo=timezone.utc)

# Los umbrales de la trucha arcoíris, copiados de V17__parametros_de_la_trucha.sql. Están aquí
# para poder afirmar en las pruebas que una lectura está dentro de rango y aun así merece aviso,
# que es lo que distingue a este servicio del sistema experto.
RANGO_TEMPERATURA = an.Rango("temp_c", critico_min=6.0, critico_max=18.0,
                             optimo_min=10.0, optimo_max=16.0)
RANGO_PH = an.Rango("ph", critico_min=6.0, critico_max=9.0,
                    optimo_min=6.5, optimo_max=8.5)
# El oxígeno no tiene tope por arriba: más oxígeno disuelto no perjudica al pez
RANGO_OXIGENO = an.Rango("oxigeno_mg_l", critico_min=5.0, critico_max=None,
                         optimo_min=6.0, optimo_max=None)
RANGOS = [RANGO_TEMPERATURA, RANGO_PH, RANGO_OXIGENO]


def lecturas(valores, variable="temp_c", cada_horas=6.0, desde=INICIO):
    """Una lectura por valor, separadas en el tiempo, con las demás variables vacías."""
    return [an.Lectura(medido_en=desde + timedelta(hours=cada_horas * i), **{variable: v})
            for i, v in enumerate(valores)]


def completas(filas, cada_horas=6.0, desde=INICIO):
    """Lecturas con las tres variables: (temp, ph, oxigeno)."""
    return [an.Lectura(medido_en=desde + timedelta(hours=cada_horas * i),
                       temp_c=t, ph=p, oxigeno_mg_l=o)
            for i, (t, p, o) in enumerate(filas)]


def de_clase(analisis, clase):
    return [h for h in analisis.hallazgos if h.clase == clase]


# ---------- sin datos no se opina ----------

def test_una_sola_lectura_no_alcanza_para_nada():
    analisis = an.analizar(lecturas([12.0]), RANGOS)
    assert analisis.hallazgos == []
    assert analisis.detectores == []
    assert not analisis.suficientes_datos


def test_con_pocas_lecturas_ningun_detector_corre():
    analisis = an.analizar(lecturas([12.0, 12.1, 11.9, 12.2, 15.0]), RANGOS)
    assert not analisis.suficientes_datos
    assert analisis.hallazgos == []
    # Y aun así informa cuántas miró: el que llama tiene que poder saber por qué no hubo respuesta
    assert analisis.lecturas_usadas == 5


# ---------- desviación: la costumbre del estanque ----------

def _estanque_de_manantial(n=20):
    """Un estanque alimentado por manantial: 11 °C todo el año, con muy poca variación."""
    azar = np.random.default_rng(5)
    return list(np.round(11.0 + azar.normal(0, 0.15, n), 2))


def test_la_desviacion_avisa_de_un_cambio_que_el_umbral_fijo_no_ve():
    """
    La razón de ser del detector, puesta a prueba.

    El estanque corre a 11 °C. Llega una lectura de 15 °C. Para el sistema experto eso está dentro
    del rango *óptimo* de la trucha (10 a 16 °C), así que no diría absolutamente nada. Para este
    estanque es un salto de cuatro grados que merece buscarle la causa.
    """
    historia = _estanque_de_manantial()
    analisis = an.analizar(lecturas(historia + [15.0]), RANGOS)

    # Primero: el sistema experto de verdad no tendría nada que decir de esta lectura
    assert RANGO_TEMPERATURA.optimo_min <= 15.0 <= RANGO_TEMPERATURA.optimo_max
    assert RANGO_TEMPERATURA.fuera_del_optimo(15.0) is False

    hallazgos = de_clase(analisis, "desviacion")
    assert len(hallazgos) == 1
    assert hallazgos[0].variable == "temp_c"
    assert "15" in hallazgos[0].mensaje
    # Dice contra qué lo compara, con la costumbre real del estanque
    assert f"{np.median(historia):.1f}" in hallazgos[0].mensaje
    # Y no grita: el pez está bien, solo cambió algo
    assert hallazgos[0].nivel == "advertencia"


def test_la_desviacion_deja_pasar_una_lectura_de_siempre():
    historia = _estanque_de_manantial()
    analisis = an.analizar(lecturas(historia + [11.05]), RANGOS)
    assert de_clase(analisis, "desviacion") == []
    # El detector corrió: su silencio es una respuesta, no una falta de datos
    assert "desviacion" in analisis.detectores


def test_la_urgencia_la_decide_el_pez_y_no_el_tamano_del_puntaje():
    """
    El error que cometió la primera versión de este módulo, convertido en prueba.

    En un estanque de manantial, que corre a 11 °C con variación de décimas, una lectura de 12 °C
    saca un puntaje enorme. Con el nivel sacado del puntaje, eso salía "crítica": el mismo grito
    que el sistema experto reserva para agua donde la trucha no vive. Y la trucha en 12 °C está
    perfecta. Para crítica tienen que darse las dos cosas: raro para el estanque Y malo para el pez.
    """
    historia = _estanque_de_manantial()

    dentro = de_clase(an.analizar(lecturas(historia + [12.0]), RANGOS), "desviacion")[0]
    fuera = de_clase(an.analizar(lecturas(historia + [19.0]), RANGOS), "desviacion")[0]

    # El puntaje del caso leve ya es grandísimo, y aun así no alcanza para crítica
    assert dentro.puntaje > est.CORTE_DESVIACION
    assert dentro.nivel == "advertencia"
    assert "tolera" in dentro.mensaje

    # 19 °C se salió del óptimo (10–16) y hasta del crítico (6–18): ahí sí
    assert RANGO_TEMPERATURA.fuera_del_optimo(19.0) is True
    assert fuera.nivel == "critica"
    assert "10–16" in fuera.mensaje


def test_sin_rangos_la_desviacion_no_se_atreve_a_llamarlo_critico():
    """Sin los umbrales no hay forma de saber si el pez lo sufre, así que no se grita."""
    historia = _estanque_de_manantial()
    hallazgos = de_clase(an.analizar(lecturas(historia + [19.0]), rangos=None), "desviacion")
    assert len(hallazgos) == 1
    assert hallazgos[0].nivel == "advertencia"


def test_la_desviacion_se_calla_en_un_estanque_sin_variacion():
    # Lectura congelada en el mismo valor: no hay costumbre contra la cual comparar
    analisis = an.analizar(lecturas([11.0] * 20 + [15.0]), RANGOS)
    assert de_clase(analisis, "desviacion") == []
    assert "desviacion" not in analisis.detectores


# ---------- tendencia: la bajada que todavía no cruzó ----------

def _oxigeno_que_baja(por_dia, dias=6.0, n=24, desde=8.6):
    horas = np.linspace(0, dias * 24, n)
    valores = desde - (por_dia / 24.0) * horas
    return [an.Lectura(medido_en=INICIO + timedelta(hours=float(h)), oxigeno_mg_l=float(v))
            for h, v in zip(horas, valores)]


def test_la_tendencia_avisa_antes_de_que_el_umbral_se_cruce():
    """
    El oxígeno baja de 8.6 y va en 6.2. El mínimo crítico es 5.0, así que el sistema experto
    todavía no tiene nada que decir. La pendiente sí: a este ritmo lo cruza en pocos días.
    """
    historia = _oxigeno_que_baja(por_dia=0.4, dias=6.0)
    ultimo = historia[-1].oxigeno_mg_l
    assert ultimo > RANGO_OXIGENO.critico_min, "la prueba exige que todavia no se haya cruzado"

    analisis = an.analizar(historia, RANGOS)
    hallazgos = de_clase(analisis, "tendencia")

    assert len(hallazgos) == 1
    assert hallazgos[0].variable == "oxigeno_mg_l"
    assert hallazgos[0].nivel in ("advertencia", "critica")
    assert "bajando" in hallazgos[0].mensaje
    assert "5" in hallazgos[0].mensaje  # nombra el limite al que llegaria


def test_la_tendencia_no_corre_en_una_ventana_de_menos_de_tres_dias():
    historia = _oxigeno_que_baja(por_dia=0.4, dias=2.0)
    analisis = an.analizar(historia, RANGOS)
    # Con dos días no hay cómo separar una bajada del ciclo del día, así que no se opina
    assert "tendencia" not in analisis.detectores
    assert de_clase(analisis, "tendencia") == []


def test_una_serie_plana_corre_pero_no_encuentra_tendencia():
    azar = np.random.default_rng(2)
    horas = np.linspace(0, 6 * 24, 30)
    valores = 8.6 + azar.normal(0, 0.12, 30)
    historia = [an.Lectura(medido_en=INICIO + timedelta(hours=float(h)), oxigeno_mg_l=float(v))
                for h, v in zip(horas, valores)]

    analisis = an.analizar(historia, RANGOS)
    assert "tendencia" in analisis.detectores
    assert de_clase(analisis, "tendencia") == []


def test_una_bajada_lejana_informa_pero_no_alarma():
    # 0.05 mg/L por día desde 8.6: al mínimo crítico le faltan más de 70 días
    historia = _oxigeno_que_baja(por_dia=0.05, dias=6.0)
    hallazgos = de_clase(an.analizar(historia, RANGOS), "tendencia")
    assert len(hallazgos) == 1
    assert hallazgos[0].nivel == "informe"


def test_sin_rangos_la_tendencia_informa_sin_poder_proyectar():
    historia = _oxigeno_que_baja(por_dia=0.4, dias=6.0)
    hallazgos = de_clase(an.analizar(historia, rangos=None), "tendencia")
    assert len(hallazgos) == 1
    assert hallazgos[0].nivel == "informe"
    assert "crítico" not in hallazgos[0].mensaje


# ---------- combinación: lo que ninguna variable sola delata ----------

def _estanque_con_ciclo(n=40):
    """Temperatura y pH suben juntos con el sol; el oxígeno baja cuando el agua se calienta."""
    azar = np.random.default_rng(7)
    paso = np.linspace(0.0, 1.0, n)
    temp = 12.0 + 5.0 * paso + azar.normal(0, 0.15, n)
    ph = 6.8 + 0.7 * paso + azar.normal(0, 0.03, n)
    oxi = 9.4 - 1.8 * paso + azar.normal(0, 0.10, n)
    return [(float(t), float(p), float(o)) for t, p, o in zip(temp, ph, oxi)]


def test_la_combinacion_atrapa_agua_fria_con_ph_alto():
    historia = _estanque_con_ciclo()
    # Cada valor por separado es de los que el estanque produce todos los días
    rara = (12.3, 7.48, 9.3)
    analisis = an.analizar(completas(historia + [rara]), RANGOS)

    assert de_clase(analisis, "desviacion") == [], "ninguna variable sola se sale"
    hallazgos = de_clase(analisis, "combinacion")
    assert len(hallazgos) == 1
    assert hallazgos[0].variable is None
    assert hallazgos[0].nivel == "advertencia"


def test_la_combinacion_deja_pasar_una_lectura_que_sigue_el_patron():
    historia = _estanque_con_ciclo()
    # Agua tibia, pH alto, oxígeno bajo: exactamente lo que este estanque hace al mediodía
    analisis = an.analizar(completas(historia + [(16.8, 7.47, 7.7)]), RANGOS)
    assert de_clase(analisis, "combinacion") == []
    assert "combinacion" in analisis.detectores


def test_la_combinacion_no_juzga_una_lectura_incompleta():
    historia = _estanque_con_ciclo()
    media = an.Lectura(medido_en=INICIO + timedelta(days=20), temp_c=12.3, ph=7.48)
    analisis = an.analizar(completas(historia) + [media], RANGOS)
    assert "combinacion" not in analisis.detectores


# ---------- atípica: el hueco entre los dos grupos ----------

def _estanque_de_dos_grupos(n=60):
    """
    Un estanque que solo se mide de día o de noche, nunca en medio.

    De día el agua está tibia, el pH alto y el oxígeno bajo; de noche al contrario. Las lecturas
    no forman una nube: forman dos grupos con un hueco entre ellos.
    """
    azar = np.random.default_rng(3)
    mitad = n // 2
    dia = np.column_stack([azar.normal(16.5, 0.35, mitad), azar.normal(7.55, 0.05, mitad),
                           azar.normal(7.4, 0.25, mitad)])
    noche = np.column_stack([azar.normal(12.5, 0.35, mitad), azar.normal(6.85, 0.05, mitad),
                             azar.normal(9.4, 0.25, mitad)])
    # Intercaladas, como llegarían de verdad: día, noche, día, noche
    filas = []
    for d, no in zip(dia, noche):
        filas.append(tuple(float(x) for x in d))
        filas.append(tuple(float(x) for x in no))
    return filas


def test_mahalanobis_declara_la_mas_normal_de_todas_una_lectura_que_nunca_pasa():
    """
    El defecto que justifica tener un modelo, medido antes de corregirlo.

    La lectura del hueco cae justo en el centro de los dos grupos, y Mahalanobis mide contra el
    centro. Le sale una distancia bajísima: la declara más normal que las lecturas reales.
    """
    nube = np.array(_estanque_de_dos_grupos())
    hueco = np.array([14.5, 7.20, 8.4])

    distancia_hueco = est.mahalanobis(hueco, nube)
    distancias_reales = [est.mahalanobis(f, np.delete(nube, i, axis=0))
                         for i, f in enumerate(nube)]

    assert distancia_hueco is not None
    assert distancia_hueco < est.corte_mahalanobis(3), "no la marca"
    # Y no por poco: queda por debajo de casi todas las lecturas de verdad
    assert distancia_hueco < float(np.percentile(distancias_reales, 10))


def test_el_bosque_si_marca_la_lectura_del_hueco():
    """
    Lo que el bosque aporta. No es infalible: medido, atrapa entre el 40% y el 60% de estos casos
    según cuánta historia tenga el estanque, con menos del 8% de falsas alarmas. Este es uno de
    los que atrapa, y la prueba fija la semilla para que siga siendo reproducible.
    """
    historia = _estanque_de_dos_grupos()
    analisis = an.analizar(completas(historia + [(14.5, 7.20, 8.4)]), RANGOS)

    hallazgos = de_clase(analisis, "atipica")
    assert len(hallazgos) == 1
    assert hallazgos[0].nivel == "informe", "no puede explicarse, no puede alarmar"
    assert "más rara que cualquiera" in hallazgos[0].mensaje


def test_el_bosque_deja_pasar_una_lectura_de_uno_de_los_dos_grupos():
    historia = _estanque_de_dos_grupos()
    # Una lectura corriente del grupo del día
    analisis = an.analizar(completas(historia + [(16.4, 7.54, 7.45)]), RANGOS)
    assert de_clase(analisis, "atipica") == []
    assert "atipica" in analisis.detectores


def test_el_bosque_se_calla_cuando_otro_detector_ya_explico_la_lectura():
    """El reparto del trabajo: si hay un hallazgo que se puede explicar, el débil no agrega nada."""
    historia = _estanque_de_dos_grupos()
    # Oxígeno en 2.0: lejísimos de todo, lo atrapa la desviación
    analisis = an.analizar(completas(historia + [(14.5, 7.20, 2.0)]), RANGOS)

    assert de_clase(analisis, "desviacion"), "la desviacion tiene que haberla visto"
    assert de_clase(analisis, "atipica") == []
    assert "atipica" not in analisis.detectores


def test_el_bosque_no_corre_sin_suficiente_historia():
    historia = _estanque_de_dos_grupos(n=30)  # por debajo de MINIMO_BOSQUE
    analisis = an.analizar(completas(historia + [(14.5, 7.20, 8.4)]), RANGOS)
    assert "atipica" not in analisis.detectores


# ---------- lo que la tesis necesita poder repetir ----------

def test_dos_llamadas_con_los_mismos_datos_dan_exactamente_lo_mismo():
    """
    Sin la semilla fija del bosque, ningún número del documento se podría volver a reproducir.
    """
    datos = completas(_estanque_de_dos_grupos() + [(14.5, 7.20, 8.4)])
    primera = an.analizar(datos, RANGOS)
    segunda = an.analizar(datos, RANGOS)

    assert primera.detectores == segunda.detectores
    assert [(h.clase, h.nivel, h.puntaje, h.mensaje) for h in primera.hallazgos] \
        == [(h.clase, h.nivel, h.puntaje, h.mensaje) for h in segunda.hallazgos]


def test_el_analisis_dice_siempre_cuanto_mide_y_cuanto_cubre():
    historia = _estanque_con_ciclo()
    analisis = an.analizar(completas(historia, cada_horas=6.0), RANGOS)
    assert analisis.lecturas_usadas == len(historia)
    # 40 lecturas cada 6 horas son algo más de 9 días
    assert analisis.dias_cubiertos == pytest.approx(9.75, abs=0.01)


def test_el_historial_se_recorta_al_tope():
    muchas = completas(_estanque_con_ciclo(n=1) * (an.TOPE_LECTURAS + 50), cada_horas=1.0)
    analisis = an.analizar(muchas, RANGOS)
    assert analisis.lecturas_usadas == an.TOPE_LECTURAS


def test_el_historial_se_ordena_aunque_llegue_revuelto():
    """
    Las lecturas llegan de varios celulares que sincronizan cuando agarran señal, así que el orden
    en que entran a la base no es el orden en que se midieron. Todo este módulo razona sobre el
    tiempo, de modo que si no ordenara, "la lectura actual" podría ser una de la semana pasada.
    """
    historia = _estanque_de_manantial()
    en_orden = lecturas(historia + [15.0])
    revuelto = [en_orden[-1]] + en_orden[:-1]  # la mas nueva, de primera

    assert an.analizar(revuelto, RANGOS).hallazgos == an.analizar(en_orden, RANGOS).hallazgos
    assert de_clase(an.analizar(revuelto, RANGOS), "desviacion")


def test_las_lecturas_sin_zona_horaria_no_rompen_el_analisis():
    """Por la misma razón que en crecimiento: el tiempo es la base de todo este módulo."""
    historia = _estanque_de_manantial()
    sin_zona = [an.Lectura(medido_en=datetime(2026, 10, 1, 6) + timedelta(hours=6 * i), temp_c=v)
                for i, v in enumerate(historia + [15.0])]
    analisis = an.analizar(sin_zona, RANGOS)
    assert de_clase(analisis, "desviacion")
    # 21 lecturas cada 6 horas son 20 intervalos: 5 dias justos
    assert analisis.dias_cubiertos == pytest.approx(5.0, abs=0.01)
