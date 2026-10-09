-- Mismo caso que el tipo de estanque: el estado del lote solo puede ser activo o cerrado.
UPDATE lotes SET estado = 'activo' WHERE estado NOT IN ('activo', 'cerrado');

ALTER TABLE lotes
    ADD CONSTRAINT ck_lotes_estado CHECK (estado IN ('activo', 'cerrado'));
