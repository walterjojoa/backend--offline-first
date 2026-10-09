-- El resumen del lote busca la ultima lectura que traiga temperatura, porque la racion de
-- alimento depende de ella:
--   findFirstByEstanqueIdAndTempCIsNotNullOrderByRegistradoEnDesc
--
-- Una lectura puede traer solo pH: la sonda de pH y la de temperatura no siempre reportan
-- juntas. Si la de temperatura se cae unos dias, el indice compuesto de V10 tiene que ir
-- descartando todas las lecturas de pH hasta encontrar la ultima buena. Un indice parcial
-- solo contiene las filas que sirven, asi que la primera que lee ya es la respuesta.
--
-- Medido en PostgreSQL 16 con un estanque que tiene 10.000 lecturas de solo pH encima de
-- la ultima lectura con temperatura (el caso de la sonda caida una semana):
--   con este indice parcial:   4 buffers
--   con el compuesto de V10: 226 buffers, 10.000 filas descartadas
--
-- Va en la carpeta de PostgreSQL porque H2 no soporta indices parciales.
CREATE INDEX ix_lecturas_agua_temperatura
    ON lecturas_agua (estanque_id, registrado_en DESC)
 WHERE temp_c IS NOT NULL;
