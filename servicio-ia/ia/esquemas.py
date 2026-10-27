"""
El contrato HTTP del servicio: lo que entra y lo que sale, por escrito.

Los nombres de los campos están en español y con guion bajo, igual que el resto de la API del
proyecto (`registrado_en`, `oxigeno_mg_l`, `cantidad_inicial`). No es una preferencia estética: son
los mismos nombres de las columnas de la base y de los eventos que manda el celular, así que el
backend Java puede armar estos cuerpos copiando lo que ya tiene y sin inventar una traducción en
el medio. Una capa de nombres distintos es una capa donde se pierden campos en silencio.

Estos modelos son la frontera. Adentro, los detectores trabajan con las clases de anomalias.py y
crecimiento.py, que no saben nada de HTTP y por eso se pueden probar sin levantar el servicio.
"""

from __future__ import annotations

from datetime import date, datetime
from typing import Literal
from uuid import UUID

from pydantic import BaseModel, Field

from ia import anomalias as an
from ia import crecimiento as cre
from ia.hallazgo import Hallazgo


# ---------- lo que sale, y es común a los dos análisis ----------

class HallazgoSalida(BaseModel):
    clase: str
    nivel: Literal["informe", "advertencia", "critica"]
    mensaje: str
    puntaje: float
    variable: str | None = None
    medido_en: datetime | None = None

    @classmethod
    def de(cls, h: Hallazgo) -> "HallazgoSalida":
        return cls(clase=h.clase, nivel=h.nivel, mensaje=h.mensaje, puntaje=h.puntaje,
                   variable=h.variable, medido_en=h.medido_en)


# ---------- análisis del agua ----------

class LecturaEntrada(BaseModel):
    """Una lectura, tal como está en lecturas_agua. Cualquier variable puede venir vacía."""
    registrado_en: datetime
    temp_c: float | None = None
    ph: float | None = None
    oxigeno_mg_l: float | None = None

    def interna(self) -> an.Lectura:
        return an.Lectura(medido_en=self.registrado_en, temp_c=self.temp_c, ph=self.ph,
                          oxigeno_mg_l=self.oxigeno_mg_l)


class RangoEntrada(BaseModel):
    """Una fila de parametros_rango. Sin ella el servicio no puede graduar la urgencia."""
    variable: str
    critico_min: float
    critico_max: float | None = None
    optimo_min: float | None = None
    optimo_max: float | None = None

    def interno(self) -> an.Rango:
        return an.Rango(variable=self.variable, critico_min=self.critico_min,
                        critico_max=self.critico_max, optimo_min=self.optimo_min,
                        optimo_max=self.optimo_max)


class AnalisisAguaEntrada(BaseModel):
    estanque_id: UUID
    # El historial del propio estanque: es lo que define qué es normal *ahí*. Un mes es la ventana
    # recomendada; menos no alcanza para fijar la costumbre y más arrastra el cambio de estación.
    historial: list[LecturaEntrada] = Field(min_length=1)
    rangos: list[RangoEntrada] = Field(default_factory=list)


class AnalisisAguaSalida(BaseModel):
    estanque_id: UUID
    lecturas_usadas: int
    dias_cubiertos: float
    # Cuáles alcanzaron a correr. Vacío significa que no había datos para ninguno, y entonces el
    # silencio del servicio no es "todo bien": es "no sé".
    detectores: list[str]
    suficientes_datos: bool
    hallazgos: list[HallazgoSalida]

    @classmethod
    def de(cls, estanque_id: UUID, a: an.Analisis) -> "AnalisisAguaSalida":
        return cls(estanque_id=estanque_id, lecturas_usadas=a.lecturas_usadas,
                   dias_cubiertos=a.dias_cubiertos, detectores=a.detectores,
                   suficientes_datos=a.suficientes_datos,
                   hallazgos=[HallazgoSalida.de(h) for h in a.hallazgos])


# ---------- proyección del lote ----------

class BiometriaEntrada(BaseModel):
    registrado_en: datetime
    peso_promedio_g: float
    muestra: int | None = None

    def interna(self) -> cre.Biometria:
        return cre.Biometria(medido_en=self.registrado_en, peso_promedio_g=self.peso_promedio_g,
                             muestra=self.muestra)


class MortalidadEntrada(BaseModel):
    registrado_en: datetime
    cantidad: int

    def interna(self) -> cre.Mortalidad:
        return cre.Mortalidad(medido_en=self.registrado_en, cantidad=self.cantidad)


class TemperaturaEntrada(BaseModel):
    registrado_en: datetime
    temp_c: float

    def interna(self) -> cre.Temperatura:
        return cre.Temperatura(medido_en=self.registrado_en, temp_c=self.temp_c)


class ProyeccionLoteEntrada(BaseModel):
    lote_id: UUID
    fecha_siembra: date | None = None
    cantidad_inicial: int | None = None
    peso_inicial_g: float | None = None
    biometrias: list[BiometriaEntrada] = Field(default_factory=list)
    mortalidades: list[MortalidadEntrada] = Field(default_factory=list)
    # Las del estanque del lote: la trucha crece con el calor acumulado, no con los días
    temperaturas: list[TemperaturaEntrada] = Field(default_factory=list)
    dias_adelante: int = Field(default=30, ge=1, le=365)
    peso_objetivo_g: float | None = Field(default=None, gt=0)

    def lote(self) -> cre.Lote:
        return cre.Lote(sembrado_en=self.fecha_siembra, cantidad_inicial=self.cantidad_inicial,
                        peso_inicial_g=self.peso_inicial_g)


class ProyeccionLoteSalida(BaseModel):
    lote_id: UUID
    dias_adelante: int
    # --- crecimiento ---
    tgc: float | None
    tgc_ajustado: bool
    """False significa que es el valor de referencia de la literatura y no el de esta finca."""
    fuente_tgc: str | None
    temp_media_c: float | None
    peso_actual_g: float | None
    peso_proyectado_g: float | None
    dias_para_objetivo: int | None
    fecha_objetivo: date | None
    # --- supervivencia: es un techo, no una medición (ver crecimiento.py) ---
    cantidad_actual: int | None
    cantidad_proyectada: int | None
    supervivencia_pct: float | None
    muertes_por_dia: float | None
    # --- el resto ---
    hallazgos: list[HallazgoSalida]
    notas: list[str]
    """Lo que no se pudo calcular y por qué. Es la parte que evita leer un None como un cero."""

    @classmethod
    def de(cls, lote_id: UUID, p: cre.Proyeccion) -> "ProyeccionLoteSalida":
        return cls(lote_id=lote_id, dias_adelante=p.dias_adelante, tgc=p.tgc,
                   tgc_ajustado=p.tgc_ajustado, fuente_tgc=p.fuente_tgc,
                   temp_media_c=p.temp_media_c, peso_actual_g=p.peso_actual_g,
                   peso_proyectado_g=p.peso_proyectado_g,
                   dias_para_objetivo=p.dias_para_objetivo, fecha_objetivo=p.fecha_objetivo,
                   cantidad_actual=p.cantidad_actual, cantidad_proyectada=p.cantidad_proyectada,
                   supervivencia_pct=p.supervivencia_pct, muertes_por_dia=p.muertes_por_dia,
                   hallazgos=[HallazgoSalida.de(h) for h in p.hallazgos], notas=p.notas)


# ---------- salud ----------

class Salud(BaseModel):
    estado: Literal["vivo"]
    version: str
    # Qué detectores sabe correr esta versión, para que el backend no tenga que adivinar si vale la
    # pena llamarla después de un despliegue.
    detectores: list[str]
