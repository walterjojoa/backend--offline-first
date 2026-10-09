-- El lote tenia estado activo/cerrado pero ninguna fecha de cierre, asi que no se podia
-- calcular cuanto duro el ciclo: de la siembra a la cosecha. Ese es el dato con el que el
-- productor compara un ciclo contra otro y decide si le fue mejor o peor.
ALTER TABLE lotes ADD COLUMN fecha_cierre DATE;

-- Los lotes que ya estaban cerrados no tienen fecha real de cierre. Se les pone la de hoy
-- para poder exigir la coherencia de aqui en adelante.
UPDATE lotes SET fecha_cierre = CURRENT_DATE WHERE estado = 'cerrado' AND fecha_cierre IS NULL;

-- Las dos columnas tienen que contar lo mismo: un lote cerrado dice cuando se cerro, y uno
-- activo no puede tener fecha de cierre. Sin esto quedan lotes "activos" con fecha de
-- cosecha, que es justo el tipo de dato que arruina un reporte sin que nadie lo note.
ALTER TABLE lotes ADD CONSTRAINT ck_lotes_cierre CHECK (
    (estado = 'cerrado' AND fecha_cierre IS NOT NULL)
    OR (estado = 'activo' AND fecha_cierre IS NULL)
);

-- No se puede cosechar antes de sembrar.
ALTER TABLE lotes ADD CONSTRAINT ck_lotes_orden_fechas CHECK (
    fecha_cierre IS NULL OR fecha_siembra IS NULL OR fecha_cierre >= fecha_siembra
);
