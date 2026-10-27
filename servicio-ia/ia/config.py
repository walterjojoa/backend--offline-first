"""
Lo que el servicio lee del entorno. Nada de secretos en el código.

Las variables se llaman igual que las del backend Java (API_KEY) para que el despliegue no tenga
dos vocabularios. En Render se ponen en el panel del servicio; en la máquina de uno, en el entorno
o simplemente no se ponen.
"""

from __future__ import annotations

import logging
import os
from dataclasses import dataclass
from functools import lru_cache

log = logging.getLogger("ia")


@dataclass(frozen=True)
class Ajustes:
    api_key: str | None


@lru_cache(maxsize=1)
def ajustes() -> Ajustes:
    """
    Se lee una vez. El aviso de la clave faltante también sale una vez y no en cada petición: un
    aviso que aparece mil veces por minuto es un aviso que nadie lee.
    """
    clave = os.environ.get("API_KEY") or None
    if not clave:
        log.warning("Sin API_KEY, el servicio de IA queda abierto a quien lo alcance. "
                    "Está bien en la máquina de uno; en un servidor hay que ponerla.")
    return Ajustes(api_key=clave)
