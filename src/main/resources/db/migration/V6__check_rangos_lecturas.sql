-- Rangos fisicamente posibles, los mismos que valida el DTO con @DecimalMin/@DecimalMax.
-- No son los rangos optimos de la trucha (eso lo decide Reglas.java y genera alertas):
-- son el limite de lo que puede medir una sonda sin estar averiada o mal calibrada.
--
-- Las columnas admiten NULL porque una lectura puede traer solo pH o solo temperatura;
-- en SQL un CHECK sobre NULL no falla, asi que esos casos siguen entrando.
ALTER TABLE lecturas_agua
    ADD CONSTRAINT ck_lecturas_agua_temp_c CHECK (temp_c BETWEEN -5 AND 40);
ALTER TABLE lecturas_agua
    ADD CONSTRAINT ck_lecturas_agua_ph CHECK (ph BETWEEN 0 AND 14);
ALTER TABLE lecturas_agua
    ADD CONSTRAINT ck_lecturas_agua_oxigeno CHECK (oxigeno_mg_l BETWEEN 0 AND 30);
