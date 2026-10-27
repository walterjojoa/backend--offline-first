"""
Estas pruebas no comprueban que las funciones corran: comprueban las afirmaciones que el módulo
hace en sus comentarios. Cada una monta el caso real que justifica haber elegido la medida
robusta, y falla si la medida clásica habría servido igual.
"""

import numpy as np
import pytest

from ia import estadistica as est


# ---------- dispersión ----------

def test_mad_de_valores_identicos_es_cero():
    # Un estanque con lectura congelada no tiene variación. Que dé cero es lo correcto; lo que
    # importa es que quien divida lo mire antes.
    assert est.mad(np.array([9.0, 9.0, 9.0, 9.0])) == 0.0


def test_puntaje_robusto_no_responde_sin_variacion():
    assert est.puntaje_robusto(6.0, np.array([9.0] * 10)) is None


def test_puntaje_robusto_sin_historial_no_responde():
    assert est.puntaje_robusto(6.0, np.array([])) is None


def test_el_puntaje_robusto_marca_la_caida_de_oxigeno():
    historial = np.array([9.1, 8.9, 9.0, 9.2, 8.8, 9.0, 9.1, 8.9, 9.0, 9.1])
    puntaje = est.puntaje_robusto(6.0, historial)
    assert puntaje is not None
    assert puntaje < -est.CORTE_DESVIACION


def test_dos_lecturas_con_el_sensor_fuera_del_agua_no_tapan_el_problema():
    """
    El caso que decide la elección de mediana sobre promedio.

    Veinte lecturas normales de oxígeno, más dos tomadas con el sensor en el aire. Después llega
    una lectura de 6.0 mg/L, que es un problema de verdad. Con promedio y desviación estándar las
    dos lecturas de aire estiran tanto el "normal" que la de 6.0 pasa desapercibida.
    """
    normales = [9.0, 9.1, 8.9, 9.2, 8.8, 9.0, 9.1, 8.9, 9.0, 9.1,
                8.9, 9.0, 9.2, 8.8, 9.1, 9.0, 8.9, 9.1, 9.0, 8.9]
    aire = [20.4, 20.1]
    historial = np.array(normales + aire)
    problema = 6.0

    clasico = (problema - historial.mean()) / historial.std(ddof=1)
    robusto = est.puntaje_robusto(problema, historial)

    assert robusto is not None
    # El clásico no llega al corte: el problema quedaría sin avisar
    assert abs(clasico) < est.CORTE_DESVIACION
    # El robusto lo pasa de sobra
    assert abs(robusto) > est.CORTE_DESVIACION
    assert abs(robusto) > 4 * abs(clasico)


# ---------- tendencia ----------

def test_theil_sen_encuentra_la_bajada():
    dias = np.arange(10, dtype=float)
    oxigeno = 9.0 - 0.4 * dias
    resultado = est.pendiente_theil_sen(dias, oxigeno)
    assert resultado is not None
    pendiente, acuerdo = resultado
    assert pendiente == pytest.approx(-0.4)
    # Una bajada limpia: todas las parejas coinciden en el signo
    assert acuerdo == 1.0


@pytest.mark.parametrize("posicion, donde", [(0, "al inicio"), (5, "en el medio"), (11, "al final")])
def test_theil_sen_aguanta_una_lectura_mala_caiga_donde_caiga(posicion, donde):
    """
    Una sola lectura con el sensor fuera del agua, en tres posiciones distintas de la serie.

    Theil-Sen devuelve la pendiente real en los tres casos. Mínimos cuadrados depende de dónde
    cayó la lectura mala, porque los extremos de la serie pesan más que el centro: con la mala al
    final llega a decir que el oxígeno *sube* mientras en realidad baja 0.3 mg/L por día. Esa es
    exactamente la equivocación que no se puede cometer aquí, porque es la que deja al cuidador
    tranquilo mientras el estanque se vacía de oxígeno.
    """
    dias = np.arange(12, dtype=float)
    oxigeno = 9.0 - 0.3 * dias
    oxigeno[posicion] = 21.0

    pendiente, _ = est.pendiente_theil_sen(dias, oxigeno)
    assert pendiente == pytest.approx(-0.3), f"con la lectura mala {donde}"


def test_minimos_cuadrados_invierte_el_signo_con_una_sola_lectura_mala():
    """El contraste del caso de arriba, medido: es la razón de no usar la recta de siempre."""
    dias = np.arange(12, dtype=float)
    oxigeno = 9.0 - 0.3 * dias
    oxigeno[11] = 21.0  # la ultima lectura, con el sensor ya afuera

    minimos_cuadrados = float(np.polyfit(dias, oxigeno, 1)[0])
    pendiente, _ = est.pendiente_theil_sen(dias, oxigeno)

    assert minimos_cuadrados > 0, "miente diciendo que el oxigeno sube"
    assert pendiente < 0, "Theil-Sen mantiene el signo correcto"


def _serie_horaria(dias: float):
    """Horas de la ventana y el ciclo del día encima, que es el ruido que hay que vencer."""
    horas = np.arange(int(dias * 24), dtype=float)
    return horas, 0.3 * np.sin(horas * np.pi / 12)


def test_el_acuerdo_descarta_el_ciclo_del_dia_en_una_ventana_de_tres_dias():
    horas, ciclo = _serie_horaria(est.DIAS_MINIMOS_TENDENCIA)
    resultado = est.pendiente_theil_sen(horas, 7.2 + ciclo)
    assert resultado is not None
    _, acuerdo = resultado
    # Los ciclos completos se cancelan: no queda rumbo que reportar
    assert acuerdo < est.ACUERDO_MINIMO


def test_el_acuerdo_reconoce_una_bajada_escondida_bajo_el_ciclo_del_dia():
    horas, ciclo = _serie_horaria(est.DIAS_MINIMOS_TENDENCIA)
    # 0.25 mg/L por dia, que es poco y nunca cruzaria un umbral fijo a tiempo
    oxigeno = 9.0 - (0.25 / 24) * horas + ciclo
    pendiente, acuerdo = est.pendiente_theil_sen(horas, oxigeno)

    assert pendiente < 0
    assert acuerdo >= est.ACUERDO_MINIMO


def test_una_ventana_de_un_dia_no_alcanza_para_decidir():
    """
    Por qué existe DIAS_MINIMOS_TENDENCIA, demostrado en vez de afirmado.

    Con un solo día de datos, un ciclo limpio *sin* ninguna tendencia saca más acuerdo que el
    mínimo exigido. Si el servicio aceptara ventanas de un día, avisaría de una bajada que no
    existe cada vez que el cuidador midiera un día seguido.
    """
    horas, ciclo = _serie_horaria(1)
    _, acuerdo = est.pendiente_theil_sen(horas, 7.2 + ciclo)
    assert acuerdo > est.ACUERDO_MINIMO


def test_theil_sen_sin_dos_horas_distintas_no_responde():
    assert est.pendiente_theil_sen(np.array([3.0]), np.array([9.0])) is None
    # Dos lecturas de la misma hora: dividir por la diferencia sería dividir por cero
    assert est.pendiente_theil_sen(np.array([3.0, 3.0]), np.array([9.0, 8.0])) is None


# ---------- combinación ----------

def _historial_con_ciclo_diario(n: int = 40) -> np.ndarray:
    """
    Historial de un estanque donde temperatura y pH se mueven juntos.

    No es un invento del ejemplo: a mediodía el agua se calienta y las algas consumen CO2, y las
    dos cosas suben el pH. Así que en este estanque el agua fría viene con pH bajo y el agua
    tibia con pH alto, y nunca al revés.
    """
    paso = np.linspace(0.0, 1.0, n)
    temperatura = 12.0 + 5.0 * paso
    ph = 6.8 + 0.8 * paso
    # Un poco de ruido reproducible para que la nube tenga grosor
    azar = np.random.default_rng(7)
    temperatura = temperatura + azar.normal(0.0, 0.15, n)
    ph = ph + azar.normal(0.0, 0.03, n)
    return np.column_stack([temperatura, ph])


def test_mahalanobis_atrapa_la_combinacion_que_ninguna_variable_delata():
    """
    La afirmación central del módulo, puesta a prueba.

    Agua fría con pH alto: 12.2 °C está en lo normal de este estanque y 7.55 de pH también. Lo
    que nunca pasó es que vinieran juntos, porque el pH alto de este estanque viene del sol del
    mediodía y el agua de mediodía no está a 12 °C. Que aparezca significa que algo distinto al
    ciclo del día está moviendo el pH, y eso es justo lo que vale la pena avisar.
    """
    historial = _historial_con_ciclo_diario()
    lectura = np.array([12.2, 7.55])

    # Por separado, cada valor está dentro de lo normal: el puntaje robusto no las marca
    puntaje_temp = est.puntaje_robusto(lectura[0], historial[:, 0])
    puntaje_ph = est.puntaje_robusto(lectura[1], historial[:, 1])
    assert puntaje_temp is not None and abs(puntaje_temp) < est.CORTE_DESVIACION
    assert puntaje_ph is not None and abs(puntaje_ph) < est.CORTE_DESVIACION

    # Juntas, no: la lectura queda lejos de la nube
    distancia = est.mahalanobis(lectura, historial)
    assert distancia is not None
    assert distancia > est.corte_mahalanobis(2)


def test_mahalanobis_deja_pasar_una_lectura_que_sigue_el_patron():
    historial = _historial_con_ciclo_diario()
    # Agua tibia con pH alto: exactamente lo que este estanque hace todos los días
    distancia = est.mahalanobis(np.array([16.8, 7.57]), historial)
    assert distancia is not None
    assert distancia < est.corte_mahalanobis(2)


def test_mahalanobis_no_responde_con_pocas_filas():
    # Tres variables necesitan al menos cuatro lecturas completas para estimar la forma
    nube = np.array([[12.0, 7.0, 9.0], [13.0, 7.1, 8.9], [12.5, 7.05, 9.1]])
    assert est.mahalanobis(np.array([12.2, 7.02, 9.0]), nube) is None


def test_mahalanobis_no_se_cae_con_variables_identicas():
    # Dos columnas iguales dejan la covarianza sin inversa; la regularización lo sostiene
    columna = np.linspace(9.0, 10.0, 15)
    nube = np.column_stack([columna, columna])
    distancia = est.mahalanobis(np.array([9.5, 9.5]), nube)
    assert distancia is not None
    assert np.isfinite(distancia)


# ---------- escala ----------

def test_normalizar_deja_las_columnas_comparables():
    nube = np.column_stack([
        np.array([12.0, 13.0, 14.0, 15.0, 16.0]),      # temperatura
        np.array([7.00, 7.05, 7.10, 7.15, 7.20]),      # pH, con un rango mucho mas chico
    ])
    escalada = est.normalizar(nube)
    # Despues de escalar, las dos columnas se mueven en el mismo orden de magnitud
    assert est.mad(escalada[:, 0]) == pytest.approx(est.mad(escalada[:, 1]))


def test_normalizar_deja_en_ceros_una_columna_sin_variacion():
    nube = np.column_stack([np.array([1.0, 2.0, 3.0]), np.array([7.0, 7.0, 7.0])])
    escalada = est.normalizar(nube)
    assert np.all(escalada[:, 1] == 0.0)
