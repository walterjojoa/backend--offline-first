-- Dos estanques con el mismo nombre no son un caso valido del negocio: el cuidador los
-- distingue por el nombre en la pantalla del celular, y si hay dos "Tanque 1" no puede
-- saber en cual esta registrando la lectura.

-- Antes de crear la restriccion hay que resolver los duplicados que ya existan. Se conserva
-- el mas antiguo (el que lleva mas tiempo en uso) y a los demas se les agrega el inicio del
-- id para distinguirlos. El nombre se recorta a 68 caracteres porque la columna es VARCHAR(80)
-- y el sufijo ocupa 11.
UPDATE estanques
   SET nombre = SUBSTRING(nombre, 1, 68) || ' (' || SUBSTRING(CAST(id AS VARCHAR), 1, 8) || ')'
 WHERE EXISTS (
           SELECT 1
             FROM estanques otro
            WHERE otro.nombre = estanques.nombre
              AND (otro.servidor_en < estanques.servidor_en
                   OR (otro.servidor_en = estanques.servidor_en
                       AND CAST(otro.id AS VARCHAR) < CAST(estanques.id AS VARCHAR)))
       );

ALTER TABLE estanques
    ADD CONSTRAINT uq_estanques_nombre UNIQUE (nombre);
