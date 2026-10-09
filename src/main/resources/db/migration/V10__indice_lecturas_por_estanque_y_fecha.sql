-- La consulta real de GET /api/estanques/{id}/lecturas es:
--   WHERE estanque_id = ? AND registrado_en BETWEEN ? AND ? ORDER BY registrado_en DESC
--
-- V1 dejo dos indices de una sola columna, asi que el motor tenia que elegir uno, filtrar
-- por la otra condicion y descartar filas. Este indice compuesto resuelve las dos
-- condiciones y el orden de una sola pasada.
CREATE INDEX ix_lecturas_agua_estanque_fecha ON lecturas_agua (estanque_id, registrado_en DESC);

-- Medido en PostgreSQL 16 con 300.000 lecturas repartidas en 33 estanques, pidiendo el
-- historial de 7 dias de uno (LIMIT 500):
--   con este indice:                     17 buffers, 0 filas descartadas
--   con ix_lecturas_agua_registrado_en:  40 buffers, 1.000 filas descartadas
-- El costo del segundo crece con el numero de estanques, porque tiene que recorrer la
-- linea de tiempo de toda la finca descartando lo que no es del estanque pedido. Con solo
-- 3 estanques el planificador todavia prefiere ese; a partir de una finca de verdad
-- escoge el compuesto por su cuenta.

-- Queda redundante: cualquier consulta que filtre por estanque_id puede usar el compuesto,
-- porque estanque_id es su primera columna. Mantenerlo solo costaria escrituras, y esta es
-- la tabla que mas crece (una fila por lectura de sensor).
DROP INDEX ix_lecturas_agua_estanque_id;

-- ix_lecturas_agua_registrado_en se conserva: sirve para mirar las lecturas de toda la finca
-- en un rango de fechas, sin filtrar por estanque, que el compuesto no puede responder.
