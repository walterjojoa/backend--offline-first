"""
Pruebas del contrato HTTP.

Lo que más importa acá no son los números (de eso se encargan test_anomalias y test_crecimiento)
sino la forma: que los nombres de los campos sean los mismos que ya usa el resto del proyecto, y
que "no tengo datos para opinar" salga como una respuesta válida y no como un error. Si eso último
se rompiera, el backend acabaría tratando el silencio del servicio como un "todo bien".
"""

from datetime import datetime, timedelta, timezone

import numpy as np
import pytest
from fastapi.testclient import TestClient

from ia import anomalias as an
from ia import crecimiento as cre
from ia.api import app
from ia.config import ajustes

INICIO = datetime(2026, 10, 1, 6, 0, tzinfo=timezone.utc)
CLAVE = "clave-de-prueba-del-servicio"


@pytest.fixture
def cliente():
    return TestClient(app)


@pytest.fixture
def con_clave(monkeypatch):
    """Levanta el servicio como se despliega de verdad: con clave exigida."""
    monkeypatch.setenv("API_KEY", CLAVE)
    ajustes.cache_clear()
    yield
    ajustes.cache_clear()


def momento(i, cada_horas=6.0):
    return (INICIO + timedelta(hours=cada_horas * i)).isoformat()


def historial_de_manantial(n=20, ultima=15.0):
    """El estanque de 11 °C, en la forma en que viaja por HTTP."""
    azar = np.random.default_rng(5)
    valores = list(np.round(11.0 + azar.normal(0, 0.15, n), 2)) + [ultima]
    return [{"registrado_en": momento(i), "temp_c": float(v)} for i, v in enumerate(valores)]


RANGOS = [
    {"variable": "temp_c", "critico_min": 6.0, "critico_max": 18.0,
     "optimo_min": 10.0, "optimo_max": 16.0},
    {"variable": "ph", "critico_min": 6.0, "critico_max": 9.0,
     "optimo_min": 6.5, "optimo_max": 8.5},
    {"variable": "oxigeno_mg_l", "critico_min": 5.0, "critico_max": None,
     "optimo_min": 6.0, "optimo_max": None},
]

ESTANQUE = "11111111-1111-4111-8111-111111111111"
LOTE = "22222222-2222-4222-8222-222222222222"


# ---------- salud ----------

def test_la_salud_responde_sin_clave(cliente, con_clave):
    """
    La salud va abierta a propósito: es lo que el backend consulta para saber si vale la pena
    llamar al servicio, y una salud que exige clave no sirve para diagnosticar nada.
    """
    respuesta = cliente.get("/salud")
    assert respuesta.status_code == 200
    cuerpo = respuesta.json()
    assert cuerpo["estado"] == "vivo"
    assert "desviacion" in cuerpo["detectores"]
    assert "crecimiento" in cuerpo["detectores"]


# ---------- la clave ----------

def test_sin_clave_no_se_puede_analizar(cliente, con_clave):
    respuesta = cliente.post("/analizar/agua",
                             json={"estanque_id": ESTANQUE, "historial": historial_de_manantial()})
    assert respuesta.status_code == 401
    assert "X-API-Key" in respuesta.json()["detail"]


def test_con_la_clave_equivocada_tampoco(cliente, con_clave):
    respuesta = cliente.post("/analizar/agua", headers={"X-API-Key": "otra"},
                             json={"estanque_id": ESTANQUE, "historial": historial_de_manantial()})
    assert respuesta.status_code == 401


def test_con_la_clave_correcta_si(cliente, con_clave):
    respuesta = cliente.post("/analizar/agua", headers={"X-API-Key": CLAVE},
                             json={"estanque_id": ESTANQUE, "historial": historial_de_manantial()})
    assert respuesta.status_code == 200


def test_sin_clave_configurada_el_servicio_queda_abierto(cliente, monkeypatch):
    """Es lo que permite correrlo en la máquina de uno sin configurar nada."""
    monkeypatch.delenv("API_KEY", raising=False)
    ajustes.cache_clear()
    try:
        respuesta = cliente.post("/analizar/agua",
                                 json={"estanque_id": ESTANQUE,
                                       "historial": historial_de_manantial()})
        assert respuesta.status_code == 200
    finally:
        ajustes.cache_clear()


# ---------- análisis del agua ----------

def test_el_analisis_devuelve_el_hallazgo_con_su_mensaje_en_espanol(cliente):
    respuesta = cliente.post("/analizar/agua",
                             json={"estanque_id": ESTANQUE, "historial": historial_de_manantial(),
                                   "rangos": RANGOS})
    assert respuesta.status_code == 200
    cuerpo = respuesta.json()

    assert cuerpo["estanque_id"] == ESTANQUE
    assert cuerpo["suficientes_datos"] is True
    assert "desviacion" in cuerpo["detectores"]

    hallazgos = [h for h in cuerpo["hallazgos"] if h["clase"] == "desviacion"]
    assert len(hallazgos) == 1
    assert hallazgos[0]["nivel"] == "advertencia"
    assert hallazgos[0]["variable"] == "temp_c"
    assert "temperatura" in hallazgos[0]["mensaje"].lower()
    assert hallazgos[0]["puntaje"] > 0


def test_poca_informacion_es_una_respuesta_valida_y_no_un_error(cliente):
    """
    La distinción que sostiene todo lo demás. Si esto devolviera 400, el backend lo registraría
    como "el servicio de IA falló"; si devolviera una lista vacía sin más, lo leería como "revisé y
    está todo bien". Son tres cosas distintas y la respuesta tiene que poder decir cuál es.
    """
    respuesta = cliente.post("/analizar/agua",
                             json={"estanque_id": ESTANQUE,
                                   "historial": [{"registrado_en": momento(0), "temp_c": 11.0},
                                                 {"registrado_en": momento(1), "temp_c": 15.0}],
                                   "rangos": RANGOS})
    assert respuesta.status_code == 200
    cuerpo = respuesta.json()
    assert cuerpo["suficientes_datos"] is False
    assert cuerpo["detectores"] == []
    assert cuerpo["hallazgos"] == []
    # Y dice cuántas miró, para que se vea por qué no alcanzó
    assert cuerpo["lecturas_usadas"] == 2


def test_un_historial_vacio_si_es_un_error_de_quien_llama(cliente):
    respuesta = cliente.post("/analizar/agua",
                             json={"estanque_id": ESTANQUE, "historial": []})
    assert respuesta.status_code == 422


def test_el_estanque_tiene_que_ser_un_uuid(cliente):
    respuesta = cliente.post("/analizar/agua",
                             json={"estanque_id": "el-de-arriba",
                                   "historial": historial_de_manantial()})
    assert respuesta.status_code == 422


def test_se_pueden_mandar_lecturas_incompletas(cliente):
    """El cuidador mide lo que alcanza: a veces solo temperatura, a veces las tres."""
    historial = [{"registrado_en": momento(i), "temp_c": 12.0} for i in range(14)]
    historial.append({"registrado_en": momento(14), "ph": 7.1})
    respuesta = cliente.post("/analizar/agua",
                             json={"estanque_id": ESTANQUE, "historial": historial})
    assert respuesta.status_code == 200


def test_los_rangos_son_opcionales(cliente):
    respuesta = cliente.post("/analizar/agua",
                             json={"estanque_id": ESTANQUE, "historial": historial_de_manantial()})
    assert respuesta.status_code == 200
    # Sin rangos no puede saber si el pez lo sufre, así que no llega a crítica
    assert all(h["nivel"] != "critica" for h in respuesta.json()["hallazgos"])


# ---------- proyección del lote ----------

def cuerpo_de_lote(**extra):
    base = {
        "lote_id": LOTE,
        "fecha_siembra": "2026-06-01",
        "cantidad_inicial": 10000,
        "peso_inicial_g": 5.0,
        "biometrias": [
            {"registrado_en": "2026-06-01T06:00:00+00:00", "peso_promedio_g": 5.0, "muestra": 30},
            {"registrado_en": "2026-07-31T06:00:00+00:00", "peso_promedio_g": 35.0, "muestra": 30},
        ],
        "mortalidades": [{"registrado_en": "2026-06-15T06:00:00+00:00", "cantidad": 300}],
        "temperaturas": [{"registrado_en": f"2026-0{6 + i // 30}-{1 + i % 30:02d}T06:00:00+00:00",
                          "temp_c": 13.0} for i in range(60)],
        "dias_adelante": 30,
        "peso_objetivo_g": 250.0,
    }
    base.update(extra)
    return base


def test_la_proyeccion_devuelve_el_peso_y_la_fecha_de_cosecha(cliente):
    respuesta = cliente.post("/proyectar/lote", json=cuerpo_de_lote())
    assert respuesta.status_code == 200
    cuerpo = respuesta.json()

    assert cuerpo["lote_id"] == LOTE
    assert cuerpo["tgc_ajustado"] is True, "con dos biometrias se ajusta a esta finca"
    assert cuerpo["peso_actual_g"] == 35.0
    assert cuerpo["peso_proyectado_g"] > cuerpo["peso_actual_g"]
    assert cuerpo["dias_para_objetivo"] > 0
    assert cuerpo["fecha_objetivo"] is not None
    assert cuerpo["supervivencia_pct"] == 97.0


def test_la_proyeccion_dice_cuando_el_coeficiente_no_es_de_esta_finca(cliente):
    respuesta = cliente.post("/proyectar/lote", json=cuerpo_de_lote(biometrias=[
        {"registrado_en": "2026-07-31T06:00:00+00:00", "peso_promedio_g": 35.0}]))
    cuerpo = respuesta.json()
    assert cuerpo["tgc_ajustado"] is False
    assert cuerpo["tgc"] == cre.TGC_REFERENCIA
    assert any("referencia" in n for n in cuerpo["notas"])


def test_lo_que_no_se_puede_calcular_sale_en_notas_y_no_como_cero(cliente):
    """
    Un peso proyectado de 0 g y un peso proyectado desconocido no son lo mismo. Si el servicio
    devolviera cero, la pantalla mostraría un lote que no va a crecer nunca.
    """
    respuesta = cliente.post("/proyectar/lote", json=cuerpo_de_lote(temperaturas=[]))
    assert respuesta.status_code == 200
    cuerpo = respuesta.json()

    assert cuerpo["peso_proyectado_g"] is None
    assert cuerpo["tgc"] is None
    assert any("temperatura" in n for n in cuerpo["notas"])
    # La supervivencia sí se pudo calcular: lo que falta no tumba lo que sí hay
    assert cuerpo["supervivencia_pct"] == 97.0


def test_un_lote_sin_nada_registrado_no_devuelve_un_pronostico_inventado(cliente):
    respuesta = cliente.post("/proyectar/lote", json={"lote_id": LOTE})
    assert respuesta.status_code == 200
    cuerpo = respuesta.json()
    assert cuerpo["peso_proyectado_g"] is None
    assert cuerpo["supervivencia_pct"] is None
    assert len(cuerpo["notas"]) >= 2


def test_el_horizonte_de_proyeccion_tiene_tope(cliente):
    # Proyectar a cinco años con el coeficiente de un lote no dice nada del lote
    assert cliente.post("/proyectar/lote",
                        json=cuerpo_de_lote(dias_adelante=2000)).status_code == 422
    assert cliente.post("/proyectar/lote",
                        json=cuerpo_de_lote(dias_adelante=0)).status_code == 422


def test_el_peso_objetivo_no_puede_ser_negativo(cliente):
    assert cliente.post("/proyectar/lote",
                        json=cuerpo_de_lote(peso_objetivo_g=-5)).status_code == 422


# ---------- los nombres del contrato ----------

def test_los_campos_se_llaman_igual_que_en_la_base_de_datos():
    """
    Esto no es cosmético: el backend Java arma estos cuerpos desde sus propias entidades, y cada
    nombre que no coincida es una traducción a mano donde se pierde un campo en silencio.
    """
    from ia.esquemas import (AnalisisAguaEntrada, BiometriaEntrada, LecturaEntrada,
                             MortalidadEntrada, ProyeccionLoteEntrada)

    assert set(LecturaEntrada.model_fields) == {"registrado_en", "temp_c", "ph", "oxigeno_mg_l"}
    assert set(BiometriaEntrada.model_fields) == {"registrado_en", "peso_promedio_g", "muestra"}
    assert set(MortalidadEntrada.model_fields) == {"registrado_en", "cantidad"}
    assert "estanque_id" in AnalisisAguaEntrada.model_fields
    for campo in ("lote_id", "fecha_siembra", "cantidad_inicial", "peso_inicial_g"):
        assert campo in ProyeccionLoteEntrada.model_fields


def test_los_niveles_son_los_mismos_que_usa_el_sistema_experto():
    """Para el cuidador hay un solo vocabulario de urgencia, no dos."""
    from ia.hallazgo import NIVELES
    assert "advertencia" in NIVELES and "critica" in NIVELES
    # El que agrega este servicio, para lo que vale saber pero no amerita ir al estanque
    assert "informe" in NIVELES
