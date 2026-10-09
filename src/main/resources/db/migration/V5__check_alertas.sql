-- Las alertas las escribe el servidor desde Reglas.java, pero la restriccion deja el contrato
-- escrito en la base: el panel web puede confiar en que solo existen estos dos niveles.
ALTER TABLE alertas
    ADD CONSTRAINT ck_alertas_nivel CHECK (nivel IN ('advertencia', 'critica'));
ALTER TABLE alertas
    ADD CONSTRAINT ck_alertas_variable CHECK (variable IN ('temp_c', 'ph', 'oxigeno_mg_l'));
