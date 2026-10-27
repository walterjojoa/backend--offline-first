"""
El servicio, por HTTP. Tres rutas y nada más.

    GET  /salud              si está vivo y qué detectores sabe correr
    POST /analizar/agua      una lectura contra el historial de su estanque
    POST /proyectar/lote     hacia dónde va un lote

No tiene base de datos ni estado. Todo lo que necesita para responder llega en el cuerpo de la
petición, y eso es deliberado: el backend Java es el dueño de los datos, y este servicio es una
calculadora a la que se le pasan los números. Así se puede apagar, reiniciar o volver a desplegar
sin que nada se pierda, y se puede probar sin levantar una base.

La clave compartida es la misma idea que el X-API-Key del backend. Este servicio no es público:
solo lo llama el backend, desde el servidor.
"""

from __future__ import annotations

import logging

from fastapi import Depends, FastAPI, Header, HTTPException, status

from ia import anomalias as an
from ia import crecimiento as cre
from ia.config import ajustes
from ia.esquemas import (AnalisisAguaEntrada, AnalisisAguaSalida, ProyeccionLoteEntrada,
                         ProyeccionLoteSalida, Salud)

VERSION = "1.0.0"

log = logging.getLogger("ia")

app = FastAPI(
    title="Servicio de IA — trazabilidad de alevinos La Cocha",
    description=__doc__,
    version=VERSION,
)


def exigir_clave(x_api_key: str | None = Header(default=None)) -> None:
    """
    La misma clave que usa el backend para sus propias rutas.

    Si no hay clave configurada el servicio queda abierto, y eso solo es aceptable corriendo en la
    máquina de uno. Se avisa al arrancar (ver config.py) para que no pase inadvertido en un
    despliegue de verdad.
    """
    esperada = ajustes().api_key
    if not esperada:
        return
    if x_api_key != esperada:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED,
                            "Falta la clave del servicio de IA (X-API-Key) o no coincide")


@app.get("/salud", response_model=Salud)
def salud() -> Salud:
    """
    Lo consulta el backend antes de confiar en este servicio.

    No calcula nada a propósito: tiene que responder rápido incluso si el servicio está ocupado,
    porque su trabajo es contestar "estoy vivo", no "estoy desocupado".
    """
    return Salud(estado="vivo", version=VERSION,
                 detectores=["desviacion", "tendencia", "combinacion", "atipica",
                             "crecimiento", "mortalidad"])


@app.post("/analizar/agua", response_model=AnalisisAguaSalida,
          dependencies=[Depends(exigir_clave)])
def analizar_agua(entrada: AnalisisAguaEntrada) -> AnalisisAguaSalida:
    """
    Juzga la última lectura del historial contra las anteriores del mismo estanque.

    Con poco historial responde con la lista de detectores vacía y suficientes_datos en false. Eso
    no es un error y no se devuelve como error: es la respuesta honesta, y el backend tiene que
    poder distinguirla de "revisé y está todo bien".
    """
    analisis = an.analizar(
        [l.interna() for l in entrada.historial],
        [r.interno() for r in entrada.rangos],
    )
    if analisis.hallazgos:
        log.info("estanque %s: %d hallazgo(s) de %s", entrada.estanque_id,
                 len(analisis.hallazgos), ", ".join(analisis.detectores))
    return AnalisisAguaSalida.de(entrada.estanque_id, analisis)


@app.post("/proyectar/lote", response_model=ProyeccionLoteSalida,
          dependencies=[Depends(exigir_clave)])
def proyectar_lote(entrada: ProyeccionLoteEntrada) -> ProyeccionLoteSalida:
    """
    Peso proyectado, fecha de cosecha y supervivencia de un lote.

    Lo que no se pueda calcular sale en 'notas' diciendo qué falta registrar, en vez de salir como
    un cero. Un peso proyectado de 0 g y un peso proyectado desconocido no son lo mismo, y quien
    lea la pantalla tiene que poder notar la diferencia.
    """
    proyeccion = cre.proyectar(
        entrada.lote(),
        biometrias=[b.interna() for b in entrada.biometrias],
        mortalidades=[m.interna() for m in entrada.mortalidades],
        temperaturas=[t.interna() for t in entrada.temperaturas],
        dias_adelante=entrada.dias_adelante,
        peso_objetivo_g=entrada.peso_objetivo_g,
    )
    if proyeccion.hallazgos:
        log.info("lote %s: %d hallazgo(s)", entrada.lote_id, len(proyeccion.hallazgos))
    return ProyeccionLoteSalida.de(entrada.lote_id, proyeccion)
