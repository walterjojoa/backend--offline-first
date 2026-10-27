"""
Pruebas de la proyección del lote.

Las primeras comprueban que el modelo está bien implementado, haciendo el viaje de ida y vuelta:
se genera un lote que crece con un coeficiente conocido y se verifica que el ajuste lo recupera.
Las demás comprueban que el servicio se calla cuando no tiene datos, en vez de inventar un
pronóstico, que es la parte que más fácil se rompe sin que nadie lo note.
"""

from datetime import date, datetime, timedelta, timezone

import pytest

from ia import crecimiento as cre

UTC = timezone.utc
SIEMBRA = date(2026, 6, 1)
INICIO = datetime(2026, 6, 1, 6, 0, tzinfo=UTC)


def temperaturas(grados, dias, cada_dias=1.0):
    """Lecturas de temperatura parejas a lo largo del periodo."""
    cuantas = int(dias / cada_dias) + 1
    return [cre.Temperatura(medido_en=INICIO + timedelta(days=cada_dias * i), temp_c=grados)
            for i in range(cuantas)]


def peso_tras(peso_g, tgc, temp_c, dias):
    """El mismo modelo, escrito aparte, para generar datos sin usar el código que se prueba."""
    return (peso_g ** (1 / 3) + (tgc / 1000.0) * temp_c * dias) ** 3


def de_clase(proyeccion, clase):
    return [h for h in proyeccion.hallazgos if h.clase == clase]


# ---------- que el modelo esté bien puesto ----------

@pytest.mark.parametrize("tgc_real, temp, dias", [
    (2.0, 13.0, 60),
    (1.4, 11.0, 90),
    (2.4, 15.0, 45),
])
def test_el_ajuste_recupera_el_coeficiente_con_el_que_se_generaron_los_datos(tgc_real, temp, dias):
    """
    El viaje de ida y vuelta. Si el modelo estuviera mal despejado, esto fallaría aunque todo lo
    demás pareciera funcionar.
    """
    peso_final = peso_tras(5.0, tgc_real, temp, dias)
    biometrias = [
        cre.Biometria(medido_en=INICIO, peso_promedio_g=5.0, muestra=30),
        cre.Biometria(medido_en=INICIO + timedelta(days=dias), peso_promedio_g=peso_final, muestra=30),
    ]

    proyeccion = cre.proyectar(
        cre.Lote(sembrado_en=SIEMBRA, cantidad_inicial=10000, peso_inicial_g=5.0),
        biometrias=biometrias,
        temperaturas=temperaturas(temp, dias),
        hoy=INICIO + timedelta(days=dias),
    )

    assert proyeccion.tgc_ajustado
    assert proyeccion.tgc == pytest.approx(tgc_real, abs=0.01)
    assert proyeccion.temp_media_c == pytest.approx(temp)


def test_la_trucha_crece_con_el_calor_y_no_con_los_dias():
    """
    El hecho sobre el que descansa todo el modelo: el mismo lote, los mismos días, agua distinta.
    """
    lote = cre.Lote(sembrado_en=SIEMBRA, cantidad_inicial=10000, peso_inicial_g=5.0)
    comun = dict(biometrias=[cre.Biometria(medido_en=INICIO, peso_promedio_g=20.0)],
                 dias_adelante=60, hoy=INICIO)

    fria = cre.proyectar(lote, temperaturas=temperaturas(9.0, 1), **comun)
    tibia = cre.proyectar(lote, temperaturas=temperaturas(15.0, 1), **comun)

    assert tibia.peso_proyectado_g > fria.peso_proyectado_g
    # Y la diferencia no es marginal: 6 grados cambian el resultado de forma apreciable
    assert tibia.peso_proyectado_g > 1.5 * fria.peso_proyectado_g


def test_con_varias_biometrias_un_pesaje_malo_no_define_el_lote():
    """La mediana de los pares, no el último par: un muestreo malo no decide el pronóstico."""
    dias = 30
    pesos = [5.0]
    for _ in range(3):
        pesos.append(peso_tras(pesos[-1], 2.0, 13.0, dias))
    # El último pesaje sale bajísimo: le tocó una muestra de los peces chicos del estanque
    pesos.append(pesos[-1] * 0.6)

    biometrias = [cre.Biometria(medido_en=INICIO + timedelta(days=dias * i), peso_promedio_g=p)
                  for i, p in enumerate(pesos)]

    proyeccion = cre.proyectar(
        cre.Lote(sembrado_en=SIEMBRA, cantidad_inicial=10000),
        biometrias=biometrias,
        temperaturas=temperaturas(13.0, dias * 4),
        hoy=INICIO + timedelta(days=dias * 4),
    )
    # Tres pares buenos contra uno malo: la mediana se queda con los buenos
    assert proyeccion.tgc == pytest.approx(2.0, abs=0.05)


# ---------- cuándo se usa el de referencia, y cuándo no se proyecta ----------

def test_con_una_sola_biometria_se_usa_el_de_referencia_y_queda_dicho():
    proyeccion = cre.proyectar(
        cre.Lote(sembrado_en=SIEMBRA, cantidad_inicial=10000),
        biometrias=[cre.Biometria(medido_en=INICIO, peso_promedio_g=20.0)],
        temperaturas=temperaturas(13.0, 1),
        hoy=INICIO,
    )
    assert not proyeccion.tgc_ajustado
    assert proyeccion.tgc == cre.TGC_REFERENCIA
    assert any("referencia" in n for n in proyeccion.notas)
    assert any("segunda biometría" in n for n in proyeccion.notas)


def test_dos_biometrias_muy_juntas_no_sirven_para_ajustar():
    # Cinco días: la diferencia de peso es más error de muestreo que crecimiento
    biometrias = [
        cre.Biometria(medido_en=INICIO, peso_promedio_g=20.0),
        cre.Biometria(medido_en=INICIO + timedelta(days=5), peso_promedio_g=22.0),
    ]
    proyeccion = cre.proyectar(
        cre.Lote(sembrado_en=SIEMBRA, cantidad_inicial=10000),
        biometrias=biometrias, temperaturas=temperaturas(13.0, 5),
        hoy=INICIO + timedelta(days=5),
    )
    assert not proyeccion.tgc_ajustado
    assert any("demasiado juntas" in n for n in proyeccion.notas)


def test_sin_temperatura_no_se_proyecta_nada():
    proyeccion = cre.proyectar(
        cre.Lote(sembrado_en=SIEMBRA, cantidad_inicial=10000),
        biometrias=[cre.Biometria(medido_en=INICIO, peso_promedio_g=20.0)],
        temperaturas=[],
        hoy=INICIO,
    )
    assert proyeccion.peso_proyectado_g is None
    assert proyeccion.tgc is None
    assert any("temperatura" in n for n in proyeccion.notas)


def test_sin_biometrias_ni_peso_de_siembra_no_hay_de_donde_partir():
    proyeccion = cre.proyectar(
        cre.Lote(sembrado_en=SIEMBRA, cantidad_inicial=10000),
        temperaturas=temperaturas(13.0, 10),
        hoy=INICIO + timedelta(days=10),
    )
    assert proyeccion.peso_actual_g is None
    assert any("pesaje de muestra" in n for n in proyeccion.notas)


def test_sin_biometrias_sirve_el_peso_de_siembra():
    proyeccion = cre.proyectar(
        cre.Lote(sembrado_en=SIEMBRA, cantidad_inicial=10000, peso_inicial_g=4.0),
        temperaturas=temperaturas(13.0, 30),
        dias_adelante=30, hoy=INICIO + timedelta(days=30),
    )
    assert proyeccion.peso_actual_g == 4.0
    assert proyeccion.peso_proyectado_g > 4.0


# ---------- la fecha de cosecha, que es el número que el productor quiere ----------

def test_dice_cuantos_dias_faltan_para_el_peso_de_venta():
    proyeccion = cre.proyectar(
        cre.Lote(sembrado_en=SIEMBRA, cantidad_inicial=10000),
        biometrias=[cre.Biometria(medido_en=INICIO, peso_promedio_g=50.0)],
        temperaturas=temperaturas(13.0, 1),
        peso_objetivo_g=250.0, hoy=INICIO,
    )
    assert proyeccion.dias_para_objetivo is not None
    assert proyeccion.dias_para_objetivo > 0
    assert proyeccion.fecha_objetivo == (INICIO + timedelta(days=proyeccion.dias_para_objetivo)).date()

    # Comprobación independiente: el peso proyectado a esos días tiene que dar el objetivo
    llegada = peso_tras(50.0, proyeccion.tgc, 13.0, proyeccion.dias_para_objetivo)
    assert llegada == pytest.approx(250.0, rel=0.02)


def test_si_ya_paso_el_peso_objetivo_faltan_cero_dias():
    proyeccion = cre.proyectar(
        cre.Lote(sembrado_en=SIEMBRA, cantidad_inicial=10000),
        biometrias=[cre.Biometria(medido_en=INICIO, peso_promedio_g=300.0)],
        temperaturas=temperaturas(13.0, 1),
        peso_objetivo_g=250.0, hoy=INICIO,
    )
    assert proyeccion.dias_para_objetivo == 0


# ---------- crecimiento malo ----------

def test_un_lote_que_pierde_peso_es_critico_y_no_se_proyecta():
    biometrias = [
        cre.Biometria(medido_en=INICIO, peso_promedio_g=40.0),
        cre.Biometria(medido_en=INICIO + timedelta(days=30), peso_promedio_g=36.0),
    ]
    proyeccion = cre.proyectar(
        cre.Lote(sembrado_en=SIEMBRA, cantidad_inicial=10000),
        biometrias=biometrias, temperaturas=temperaturas(13.0, 30),
        hoy=INICIO + timedelta(days=30),
    )
    hallazgos = de_clase(proyeccion, "crecimiento")
    assert len(hallazgos) == 1
    assert hallazgos[0].nivel == "critica"
    assert "no está creciendo" in hallazgos[0].mensaje
    # Proyectar con un coeficiente negativo daría un peso cayendo a cero: no se hace
    assert proyeccion.peso_proyectado_g is None
    assert any("sin sentido" in n for n in proyeccion.notas)


def test_crecer_muy_por_debajo_de_lo_que_el_agua_permite_avisa_y_dice_donde_mirar():
    """
    El hallazgo más útil del módulo: el agua está bien y el pez igual no crece. Eso descarta la
    temperatura y apunta a ración, densidad o sanidad, que es lo que el cuidador puede cambiar.
    """
    dias = 60
    # La mitad de lo que el agua a 14 grados permitiría
    lento = cre.TGC_REFERENCIA * 0.5
    biometrias = [
        cre.Biometria(medido_en=INICIO, peso_promedio_g=20.0),
        cre.Biometria(medido_en=INICIO + timedelta(days=dias),
                      peso_promedio_g=peso_tras(20.0, lento, 14.0, dias)),
    ]
    proyeccion = cre.proyectar(
        cre.Lote(sembrado_en=SIEMBRA, cantidad_inicial=10000),
        biometrias=biometrias, temperaturas=temperaturas(14.0, dias),
        hoy=INICIO + timedelta(days=dias),
    )
    hallazgos = de_clase(proyeccion, "crecimiento")
    assert len(hallazgos) == 1
    assert hallazgos[0].nivel == "advertencia"
    assert "ración" in hallazgos[0].mensaje
    # Y sigue proyectando: el lote crece, solo que mal
    assert proyeccion.peso_proyectado_g > proyeccion.peso_actual_g


def test_un_lote_que_crece_bien_no_recibe_ningun_aviso():
    dias = 60
    biometrias = [
        cre.Biometria(medido_en=INICIO, peso_promedio_g=20.0),
        cre.Biometria(medido_en=INICIO + timedelta(days=dias),
                      peso_promedio_g=peso_tras(20.0, cre.TGC_REFERENCIA, 13.0, dias)),
    ]
    proyeccion = cre.proyectar(
        cre.Lote(sembrado_en=SIEMBRA, cantidad_inicial=10000),
        biometrias=biometrias, temperaturas=temperaturas(13.0, dias),
        hoy=INICIO + timedelta(days=dias),
    )
    assert de_clase(proyeccion, "crecimiento") == []


# ---------- supervivencia: un techo, y dicho como techo ----------

def test_la_supervivencia_se_reporta_como_techo():
    proyeccion = cre.proyectar(
        cre.Lote(sembrado_en=SIEMBRA, cantidad_inicial=1000),
        mortalidades=[cre.Mortalidad(medido_en=INICIO + timedelta(days=10), cantidad=250)],
        temperaturas=temperaturas(13.0, 30),
        hoy=INICIO + timedelta(days=30),
    )
    assert proyeccion.cantidad_actual == 750
    assert proyeccion.supervivencia_pct == 75.0

    hallazgos = de_clase(proyeccion, "mortalidad")
    assert hallazgos, "75% esta por debajo del 80% esperado"
    # Lo que no se puede saber, dicho: alevines que mueren sin registrarse no están contados
    assert "techo" in hallazgos[0].mensaje


def test_un_lote_sano_no_recibe_aviso_de_mortalidad():
    proyeccion = cre.proyectar(
        cre.Lote(sembrado_en=SIEMBRA, cantidad_inicial=1000),
        mortalidades=[cre.Mortalidad(medido_en=INICIO + timedelta(days=5), cantidad=40),
                      cre.Mortalidad(medido_en=INICIO + timedelta(days=20), cantidad=30)],
        temperaturas=temperaturas(13.0, 60),
        hoy=INICIO + timedelta(days=60),
    )
    assert proyeccion.supervivencia_pct == 93.0
    assert de_clase(proyeccion, "mortalidad") == []


def test_la_proyeccion_de_cantidad_usa_la_tasa_por_dia():
    proyeccion = cre.proyectar(
        cre.Lote(sembrado_en=SIEMBRA, cantidad_inicial=1000),
        mortalidades=[cre.Mortalidad(medido_en=INICIO + timedelta(days=i), cantidad=1)
                      for i in range(1, 51)],
        temperaturas=temperaturas(13.0, 50),
        dias_adelante=30, hoy=INICIO + timedelta(days=50),
    )
    assert proyeccion.muertes_por_dia == pytest.approx(1.0, abs=0.05)
    assert proyeccion.cantidad_actual == 950
    # A una muerte por día, en 30 días más quedan unos 920
    assert proyeccion.cantidad_proyectada == pytest.approx(920, abs=2)


def test_mas_muertes_que_sembrados_se_denuncia_como_datos_inconsistentes():
    proyeccion = cre.proyectar(
        cre.Lote(sembrado_en=SIEMBRA, cantidad_inicial=100),
        mortalidades=[cre.Mortalidad(medido_en=INICIO + timedelta(days=5), cantidad=150)],
        temperaturas=temperaturas(13.0, 30),
        hoy=INICIO + timedelta(days=30),
    )
    assert proyeccion.cantidad_actual == 0
    assert any("pasan la cantidad sembrada" in n for n in proyeccion.notas)


def test_sin_cantidad_sembrada_no_se_calcula_supervivencia():
    proyeccion = cre.proyectar(
        cre.Lote(sembrado_en=SIEMBRA),
        mortalidades=[cre.Mortalidad(medido_en=INICIO, cantidad=10)],
        temperaturas=temperaturas(13.0, 10), hoy=INICIO + timedelta(days=10),
    )
    assert proyeccion.supervivencia_pct is None
    assert any("cantidad sembrada" in n for n in proyeccion.notas)


# ---------- la mortalidad que se acelera, que el total acumulado esconde ----------

def test_la_aceleracion_se_ve_aunque_la_supervivencia_siga_buena():
    """
    Lo que el porcentaje acumulado no dice. El lote lleva 95% de supervivencia, que es excelente,
    y esta semana se está muriendo diez veces más rápido que antes. El total lo esconde; la tasa no.
    """
    muertes = [cre.Mortalidad(medido_en=INICIO + timedelta(days=i), cantidad=1) for i in range(1, 41)]
    muertes += [cre.Mortalidad(medido_en=INICIO + timedelta(days=i), cantidad=10)
                for i in range(55, 60)]
    proyeccion = cre.proyectar(
        cre.Lote(sembrado_en=SIEMBRA, cantidad_inicial=2000),
        mortalidades=muertes, temperaturas=temperaturas(13.0, 60),
        hoy=INICIO + timedelta(days=60),
    )
    assert proyeccion.supervivencia_pct > 90.0, "el acumulado se ve bien"
    acelerada = [h for h in de_clase(proyeccion, "mortalidad") if "aceleró" in h.mensaje]
    assert len(acelerada) == 1
    assert acelerada[0].nivel == "critica"


def test_dos_muertes_de_mas_no_son_una_aceleracion():
    """Sin el mínimo absoluto, pasar de una muerte a tres sería una 'triplicación'."""
    muertes = [cre.Mortalidad(medido_en=INICIO + timedelta(days=5), cantidad=1),
               cre.Mortalidad(medido_en=INICIO + timedelta(days=50), cantidad=3)]
    proyeccion = cre.proyectar(
        cre.Lote(sembrado_en=SIEMBRA, cantidad_inicial=2000),
        mortalidades=muertes, temperaturas=temperaturas(13.0, 60),
        hoy=INICIO + timedelta(days=60),
    )
    assert [h for h in de_clase(proyeccion, "mortalidad") if "aceleró" in h.mensaje] == []


def test_un_lote_que_empieza_a_morirse_de_la_nada_es_critico():
    muertes = [cre.Mortalidad(medido_en=INICIO + timedelta(days=55), cantidad=30)]
    proyeccion = cre.proyectar(
        cre.Lote(sembrado_en=SIEMBRA, cantidad_inicial=2000),
        mortalidades=muertes, temperaturas=temperaturas(13.0, 60),
        hoy=INICIO + timedelta(days=60),
    )
    hallazgos = [h for h in de_clase(proyeccion, "mortalidad") if "Empezó algo" in h.mensaje]
    assert len(hallazgos) == 1
    assert hallazgos[0].nivel == "critica"


def test_sin_mortalidades_no_se_inventa_una_aceleracion():
    proyeccion = cre.proyectar(
        cre.Lote(sembrado_en=SIEMBRA, cantidad_inicial=2000),
        mortalidades=[], temperaturas=temperaturas(13.0, 60),
        hoy=INICIO + timedelta(days=60),
    )
    assert proyeccion.supervivencia_pct == 100.0
    assert de_clase(proyeccion, "mortalidad") == []


# ---------- la zona horaria, que tumbó el servicio una vez ----------

def test_un_lote_con_muertes_pero_sin_temperatura_no_tumba_el_servicio():
    """
    El bug exacto, convertido en prueba.

    El cuidador anota las muertes antes de bajar el sensor al agua, así que un lote con
    mortalidades y sin ninguna lectura de temperatura es lo más normal del mundo. La hora "ahora"
    se sacaba de la primera temperatura, de modo que sin temperaturas quedaba sin zona horaria, y
    compararla con las mortalidades (que sí la traen) daba un error 500.
    """
    proyeccion = cre.proyectar(
        cre.Lote(sembrado_en=SIEMBRA, cantidad_inicial=1000, peso_inicial_g=5.0),
        mortalidades=[cre.Mortalidad(medido_en=INICIO + timedelta(days=5), cantidad=300)],
        temperaturas=[],
        # Sin 'hoy': es el caso de producción, donde la hora la pone el propio servicio
    )
    # Lo que sí se puede calcular, se calcula
    assert proyeccion.supervivencia_pct == 70.0
    assert proyeccion.cantidad_actual == 700
    # Y lo que no, queda dicho en vez de salir como cero
    assert proyeccion.peso_proyectado_g is None
    assert any("temperatura" in n for n in proyeccion.notas)


def test_las_fechas_sin_zona_se_toman_como_utc():
    """Pueden llegar de una prueba o de un cliente descuidado; no deben romper la comparación."""
    sin_zona = datetime(2026, 6, 1, 6, 0)  # sin tzinfo
    proyeccion = cre.proyectar(
        cre.Lote(sembrado_en=SIEMBRA, cantidad_inicial=1000),
        biometrias=[cre.Biometria(medido_en=sin_zona, peso_promedio_g=20.0)],
        temperaturas=[cre.Temperatura(medido_en=sin_zona, temp_c=13.0)],
        mortalidades=[cre.Mortalidad(medido_en=sin_zona + timedelta(days=5), cantidad=50)],
        hoy=INICIO + timedelta(days=30),
    )
    assert proyeccion.peso_actual_g == 20.0
    assert proyeccion.supervivencia_pct == 95.0
