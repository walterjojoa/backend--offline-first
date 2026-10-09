-- lectura_id era NOT NULL, asi que solo podian existir alertas de calidad de agua. Una
-- alerta de "mortalidad alta" o "supervivencia por debajo del 80%" no se podia guardar:
-- no habia lectura a la que colgarla. El sistema experto no podia crecer mas alla del agua.
--
-- H2 y PostgreSQL escriben distinto el quitar un NOT NULL, de ahi que esta version viva en
-- las carpetas por motor.
ALTER TABLE alertas ALTER COLUMN lectura_id DROP NOT NULL;
ALTER TABLE alertas ALTER COLUMN variable DROP NOT NULL;

-- Las alertas que no son del agua son de un lote, no de un estanque.
ALTER TABLE alertas ADD COLUMN lote_id UUID;
ALTER TABLE alertas ADD CONSTRAINT fk_alertas_lote FOREIGN KEY (lote_id) REFERENCES lotes (id);

-- Que la disparo. Se llena en dos pasos para poder ponerle NOT NULL sobre las filas que
-- ya existen, que por definicion son todas de lectura de agua.
ALTER TABLE alertas ADD COLUMN disparada_por VARCHAR(20);
UPDATE alertas SET disparada_por = 'lectura_agua';
ALTER TABLE alertas ALTER COLUMN disparada_por SET NOT NULL;
ALTER TABLE alertas ADD CONSTRAINT ck_alertas_disparada_por
    CHECK (disparada_por IN ('lectura_agua', 'mortalidad', 'supervivencia'));

-- V5 fijo la lista de variables a mano. Ahora que los umbrales viven en parametros_rango,
-- la llave foranea hace el mismo trabajo y se mantiene sola: si el productor agrega una
-- variable nueva a sus umbrales, las alertas de esa variable pasan sin tocar el esquema.
-- De paso impide borrar un parametro que tenga alertas en el historial.
ALTER TABLE alertas DROP CONSTRAINT ck_alertas_variable;
ALTER TABLE alertas ADD CONSTRAINT fk_alertas_variable
    FOREIGN KEY (variable) REFERENCES parametros_rango (variable);

-- Una alerta de agua tiene que traer la lectura y la variable; una de lote, el lote.
ALTER TABLE alertas ADD CONSTRAINT ck_alertas_coherente CHECK (
    (disparada_por = 'lectura_agua' AND lectura_id IS NOT NULL AND variable IS NOT NULL)
    OR (disparada_por <> 'lectura_agua' AND lote_id IS NOT NULL)
);
