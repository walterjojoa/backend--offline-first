"""
Las medidas con las que el servicio decide si una lectura es rara.

Todas son *robustas*: se calculan con medianas y no con promedios. La razón es el caso real que
este servicio tiene que atender. El cuidador baja el sensor al estanque y los primeros segundos
el aparato manda basura; o lo saca del agua y queda midiendo el aire. Esas lecturas quedan
guardadas, porque el sistema no borra eventos. Con promedios y desviación estándar, dos lecturas
de aire alcanzan para mover el "normal" del estanque y dejar de avisar cuando algo pasa de
verdad. Con medianas, no.

Nada de esto inventa datos: cada función dice que no puede responder (None) cuando no tiene con
qué, y quien la llama tiene que decidir qué hacer con ese None.
"""

from __future__ import annotations

import numpy as np

# Constante de Iglewicz y Hoaglin (1993): lo que hay que multiplicar la MAD para que, en una
# distribución normal, quede comparable con la desviación estándar.
_MAD_A_SIGMA = 0.6745

# El mismo trabajo propone 3.5 como corte del puntaje robusto. Se deja nombrado y no suelto en
# el código porque es un umbral que el productor puede querer mover.
CORTE_DESVIACION = 3.5

# Los dos números de abajo no son de la literatura: salieron de medirlos, y conviene dejar escrito
# cómo, porque son los que deciden cuándo el servicio se atreve a decir "esto viene bajando".
#
# El problema es el ciclo del día. El agua se calienta a mediodía y se enfría de noche, y el pH y
# el oxígeno la siguen. En una ventana corta ese vaivén es indistinguible de una tendencia: medido
# sobre 40 series simuladas de un día, las series *sin* tendencia sacaron acuerdo más alto (0.77 en
# el percentil 95) que las series *con* tendencia real (0.67 en el percentil 5). O sea que en una
# ventana de un día no hay umbral que sirva, porque el orden está invertido.
#
# A partir de tres días los ciclos completos se cancelan y los dos grupos se separan. Con esa
# ventana se barrieron los umbrales:
#
#     0.60 -> detecta 97% de las tendencias reales, pero avisa de más en 5% de las series planas
#     0.65 -> detecta 92%, y no avisó de más ni una vez
#     0.70 -> detecta 71%, sin ganar nada en precisión
#
# Se elige 0.65. El 5% de más del 0.60 parece poco escrito así, pero es el cuidador recibiendo una
# alarma falsa cada veinte: a la tercera deja de mirarlas, y entonces el servicio no sirve para
# nada. Perder el 8% de las tendencias más ruidosas cuesta menos que perder la confianza.
ACUERDO_MINIMO = 0.65
DIAS_MINIMOS_TENDENCIA = 3.0


def mediana(valores: np.ndarray) -> float:
    return float(np.median(valores))


def mad(valores: np.ndarray) -> float:
    """
    Desviación absoluta mediana: la mediana de lo que cada valor se aparta de la mediana.

    Devuelve 0.0 cuando todos los valores son iguales, y eso no es un error: significa que el
    estanque no ha mostrado variación y no hay con qué medir si algo se salió. Quien llame tiene
    que mirar ese cero antes de dividir.
    """
    if valores.size == 0:
        return 0.0
    centro = np.median(valores)
    return float(np.median(np.abs(valores - centro)))


def puntaje_robusto(valor: float, historial: np.ndarray) -> float | None:
    """
    Cuántas "desviaciones robustas" se aparta un valor de lo normal de su historial.

    None cuando el historial no tiene variación: ahí la pregunta no tiene respuesta, y responder
    cero (que se lee como "normal") o infinito (que se lee como "alarma") sería mentir en una de
    las dos direcciones.
    """
    if historial.size == 0:
        return None
    dispersion = mad(historial)
    if dispersion == 0.0:
        return None
    return _MAD_A_SIGMA * (valor - float(np.median(historial))) / dispersion


def pendiente_theil_sen(x: np.ndarray, y: np.ndarray) -> tuple[float, float] | None:
    """
    Pendiente de la recta que mejor describe la serie, por el método de Theil-Sen: la mediana de
    las pendientes de todas las parejas de puntos.

    Se usa esta y no mínimos cuadrados por lo mismo de arriba. Una sola lectura tomada con el
    sensor fuera del agua inclina una recta de mínimos cuadrados lo suficiente para inventar una
    tendencia que no existe; la mediana de las parejas ni se mueve.

    Devuelve (pendiente, acuerdo), donde acuerdo es la fracción de parejas que coincide en el
    signo de la pendiente mediana. Sirve para distinguir una bajada real de un vaivén sin rumbo:
    en el vaivén la mediana puede salir negativa por casualidad, pero el acuerdo queda cerca de
    la mitad.

    None si hay menos de dos puntos distintos en x.
    """
    if x.size < 2:
        return None
    # Todas las parejas i<j, descartando las que comparten el mismo x (misma hora: dividiría por cero)
    i, j = np.triu_indices(x.size, k=1)
    dx = x[j] - x[i]
    utiles = dx != 0
    if not np.any(utiles):
        return None
    pendientes = (y[j] - y[i])[utiles] / dx[utiles]

    pendiente = float(np.median(pendientes))
    if pendiente == 0.0:
        return 0.0, 0.0
    mismo_signo = np.sign(pendientes) == np.sign(pendiente)
    return pendiente, float(np.mean(mismo_signo))


def mahalanobis(punto: np.ndarray, nube: np.ndarray) -> float | None:
    """
    A cuántas unidades de la nube de puntos está este punto, contando la forma de la nube.

    Es la medida que atrapa lo que ninguna variable sola delata. En un estanque de la laguna, 17 °C
    es una temperatura corriente y 6.1 mg/L de oxígeno también; lo que no es corriente es que se
    den juntas, porque el agua tibia carga menos oxígeno y la trucha en agua tibia consume más.
    Mirando variable por variable, esa lectura pasa. Mirando la nube, no.

    El centro es la mediana de cada columna y no el promedio, por la razón de siempre. A la matriz
    de covarianza se le suma una diagonal pequeña (regularización de Tíjonov) para que el cálculo
    no se caiga cuando dos variables se mueven casi idénticas y la matriz queda sin inversa.

    None cuando la nube tiene menos filas que columnas más una: con eso no hay forma que estimar.
    """
    if nube.ndim != 2 or nube.shape[0] < nube.shape[1] + 1:
        return None

    centro = np.median(nube, axis=0)
    centradas = nube - centro
    covarianza = (centradas.T @ centradas) / max(nube.shape[0] - 1, 1)

    # La diagonal se escala con el tamaño propio de los datos: una constante fija valdría mucho
    # para el pH (que se mueve entre 6 y 8) y nada para conductividades de cientos de unidades.
    escala = float(np.trace(covarianza)) / covarianza.shape[0]
    if escala <= 0.0:
        return None
    covarianza = covarianza + np.eye(covarianza.shape[0]) * escala * 1e-6

    delta = punto - centro
    try:
        distancia2 = float(delta @ np.linalg.solve(covarianza, delta))
    except np.linalg.LinAlgError:
        return None
    # Un negativo solo sale por error de redondeo cuando el punto cae casi en el centro
    return float(np.sqrt(max(distancia2, 0.0)))


# Mahalanobis al cuadrado de una lectura normal sigue una chi cuadrado con tantos grados de
# libertad como variables. Estos son los cortes al 1%: una de cada cien lecturas normales los
# pasa por azar. Se dejan escritos y no calculados para no arrastrar scipy solo por tres números.
CORTE_MAHALANOBIS = {1: 2.576, 2: 3.035, 3: 3.368, 4: 3.644}


def corte_mahalanobis(variables: int) -> float:
    """El corte que corresponde a ese número de variables; el de 4 para cualquier cosa mayor."""
    return CORTE_MAHALANOBIS.get(variables, CORTE_MAHALANOBIS[4])


def normalizar(nube: np.ndarray) -> np.ndarray:
    """
    Pone las columnas en una escala común, usando mediana y MAD.

    El bosque de aislamiento parte por una variable a la vez, así que en teoría la escala no le
    importa. En la práctica sí conviene: las tres variables del agua tienen rangos parecidos pero
    no iguales, y dejarlas comparables hace que los cortes del árbol se reparten entre ellas en
    vez de concentrarse en la de mayor rango.

    Una columna sin variación queda en ceros, que es lo correcto: no aporta nada para separar.
    """
    centro = np.median(nube, axis=0)
    dispersion = np.array([mad(nube[:, k]) for k in range(nube.shape[1])])
    dispersion[dispersion == 0.0] = 1.0
    return (nube - centro) / dispersion
