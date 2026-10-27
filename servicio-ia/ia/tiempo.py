"""
Una sola cosa: que ninguna comparación de fechas pueda romperse por la zona horaria.

El caso que lo hizo necesario estaba en la proyección del lote. La hora "ahora" se sacaba de la
primera lectura de temperatura, así que un lote con mortalidades registradas pero sin ninguna
lectura de agua —que es lo más normal del mundo, el cuidador anota las muertes antes de bajar el
sensor— dejaba un "ahora" sin zona frente a unas mortalidades con zona, y Python no compara esas
dos cosas: tumbaba el servicio con un error 500.

La regla es normalizar en la entrada y no en cada comparación. Todas las fechas de la base son
TIMESTAMP WITH TIME ZONE, o sea que vienen en UTC; una fecha sin zona solo puede llegar de una
prueba o de un cliente descuidado, y tratarla como UTC es lo correcto y además lo que el resto del
proyecto ya supone.
"""

from __future__ import annotations

from datetime import datetime, timezone


def con_zona(momento: datetime) -> datetime:
    """La misma fecha, garantizada con zona horaria. Sin zona se asume UTC."""
    return momento if momento.tzinfo is not None else momento.replace(tzinfo=timezone.utc)


def ahora() -> datetime:
    return datetime.now(tz=timezone.utc)
