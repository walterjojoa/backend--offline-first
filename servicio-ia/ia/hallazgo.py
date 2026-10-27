"""
Lo que el servicio encuentra, en la forma en que sale por la API.

Es el único tipo que comparten el análisis del agua y la proyección del lote, y por eso vive
aparte: así ninguno de los dos tiene que importar al otro para hablar de sus resultados.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime

# Los tres niveles son los mismos que ya usa el sistema experto del backend, a propósito: para el
# cuidador no hay dos vocabularios de urgencia, hay uno. "informe" es el que agrega este servicio,
# para lo que vale saber pero no amerita ir al estanque.
NIVELES = ("informe", "advertencia", "critica")


@dataclass(frozen=True)
class Hallazgo:
    """
    clase    de dónde salió: desviacion, tendencia, combinacion, atipica, crecimiento, mortalidad
    variable la del agua, cuando el hallazgo es de una sola; None cuando mira varias a la vez
    puntaje  la medida que lo disparó, para poder ordenarlos y para que la tesis los pueda citar
    mensaje  la frase que ve el cuidador, en español y con los números adentro
    """
    clase: str
    nivel: str
    mensaje: str
    puntaje: float
    variable: str | None = None
    medido_en: datetime | None = None

    def __post_init__(self):
        if self.nivel not in NIVELES:
            raise ValueError(f"nivel desconocido: {self.nivel}")
