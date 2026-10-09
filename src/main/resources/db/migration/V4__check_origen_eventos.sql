-- De donde salio el dato: sensor, contador de alevinos, dictado por voz o escrito a mano.
-- Importa para la tesis porque permite comparar la precision del contador contra el conteo manual;
-- si el valor se ensucia, esa comparacion deja de ser posible.
UPDATE lecturas_agua  SET origen = 'manual' WHERE origen NOT IN ('sensor', 'contador', 'voz', 'manual');
UPDATE conteos        SET origen = 'manual' WHERE origen NOT IN ('sensor', 'contador', 'voz', 'manual');
UPDATE mortalidades   SET origen = 'manual' WHERE origen NOT IN ('sensor', 'contador', 'voz', 'manual');
UPDATE alimentaciones SET origen = 'manual' WHERE origen NOT IN ('sensor', 'contador', 'voz', 'manual');
UPDATE biometrias     SET origen = 'manual' WHERE origen NOT IN ('sensor', 'contador', 'voz', 'manual');

ALTER TABLE lecturas_agua
    ADD CONSTRAINT ck_lecturas_agua_origen CHECK (origen IN ('sensor', 'contador', 'voz', 'manual'));
ALTER TABLE conteos
    ADD CONSTRAINT ck_conteos_origen CHECK (origen IN ('sensor', 'contador', 'voz', 'manual'));
ALTER TABLE mortalidades
    ADD CONSTRAINT ck_mortalidades_origen CHECK (origen IN ('sensor', 'contador', 'voz', 'manual'));
ALTER TABLE alimentaciones
    ADD CONSTRAINT ck_alimentaciones_origen CHECK (origen IN ('sensor', 'contador', 'voz', 'manual'));
ALTER TABLE biometrias
    ADD CONSTRAINT ck_biometrias_origen CHECK (origen IN ('sensor', 'contador', 'voz', 'manual'));
