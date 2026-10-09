-- El tipo de estanque ya estaba restringido con @Pattern en el DTO, pero la base de datos
-- aceptaba cualquier texto si el dato entraba por el panel web, un script o SQL directo.

-- Cualquier valor que no sea de la lista pasa al valor por defecto antes de crear la
-- restriccion, para que la migracion no falle sobre datos que ya existen en Neon.
UPDATE estanques SET tipo = 'estanque' WHERE tipo NOT IN ('estanque', 'tanque', 'jaula');

ALTER TABLE estanques
    ADD CONSTRAINT ck_estanques_tipo CHECK (tipo IN ('estanque', 'tanque', 'jaula'));
