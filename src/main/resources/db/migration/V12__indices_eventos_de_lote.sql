-- Las cuatro tablas de eventos del lote tenian solo un indice sobre lote_id, pero ninguna
-- consulta filtra solo por lote: todas acotan o ordenan tambien por registrado_en.

-- El resumen recorre los conteos del lote en orden para tomar el ultimo.
CREATE INDEX ix_conteos_lote_fecha ON conteos (lote_id, registrado_en);
DROP INDEX ix_conteos_lote_id;

-- Dos consultas: el total de muertes del lote y las muertes posteriores al ultimo conteo.
-- Incluye cantidad al final para que la suma se responda con el indice, sin leer la tabla.
CREATE INDEX ix_mortalidades_lote_fecha ON mortalidades (lote_id, registrado_en, cantidad);
DROP INDEX ix_mortalidades_lote_id;

-- Kilos de alimento de la ultima semana. Mismo caso: kg va en el indice para que la suma
-- no tenga que ir a buscar cada fila.
CREATE INDEX ix_alimentaciones_lote_fecha ON alimentaciones (lote_id, registrado_en, kg);
DROP INDEX ix_alimentaciones_lote_id;

-- Solo se pide la biometria mas reciente del lote, de ahi el DESC.
CREATE INDEX ix_biometrias_lote_fecha ON biometrias (lote_id, registrado_en DESC);
DROP INDEX ix_biometrias_lote_id;

-- Medido en PostgreSQL 16 sumando los kilos de una semana sobre 80.000 registros de
-- alimentacion (10.079 filas sumadas):
--   con kg dentro del indice:  Index Only Scan, 113 buffers, 0 lecturas de la tabla
--   sin kg dentro del indice:  Bitmap Heap Scan, 178 buffers y 125 bloques de tabla leidos
-- Guardar kg y cantidad en el indice ocupa un poco mas de disco, pero la suma se responde
-- sin ir a buscar ninguna fila.
