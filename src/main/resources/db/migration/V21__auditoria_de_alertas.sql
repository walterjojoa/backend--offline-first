-- POST /api/alertas/{id}/atender ponia atendida en true y nada mas. No quedaba cuando se
-- atendio ni quien lo hizo, asi que no se podia medir cuanto tarda el cuidador en responder
-- a una alerta critica, que es justo uno de los indicadores que la tesis quiere mostrar.
ALTER TABLE alertas ADD COLUMN atendida_en TIMESTAMP WITH TIME ZONE;

-- Queda como texto libre y sin llave foranea a proposito: hoy la alerta se atiende desde el
-- panel web, que no tiene usuarios ni dispositivo_id. Guarda el id del dispositivo cuando
-- viene del celular y 'panel' cuando viene del navegador. Cuando el proyecto tenga tabla de
-- usuarios, esta columna pasa a ser llave foranea a ella.
ALTER TABLE alertas ADD COLUMN atendida_por VARCHAR(64);

-- Las alertas atendidas antes de esta migracion no tienen hora real de atencion. Se les pone
-- creada_en para poder exigir la coherencia de aqui en adelante; se reconocen porque les
-- queda atendida_en igual a creada_en y atendida_por en nulo.
UPDATE alertas SET atendida_en = creada_en WHERE atendida = true AND atendida_en IS NULL;

-- Una alerta marcada como atendida tiene que decir cuando.
ALTER TABLE alertas ADD CONSTRAINT ck_alertas_atendida_en
    CHECK (atendida = false OR atendida_en IS NOT NULL);
