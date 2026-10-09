-- Cantidades que no pueden ser negativas ni absurdas. Replican los limites del DTO
-- para que un INSERT que no pase por la API tampoco pueda guardar basura.
ALTER TABLE estanques
    ADD CONSTRAINT ck_estanques_volumen CHECK (volumen_m3 > 0);

ALTER TABLE lotes
    ADD CONSTRAINT ck_lotes_cantidad_inicial CHECK (cantidad_inicial >= 0);
ALTER TABLE lotes
    ADD CONSTRAINT ck_lotes_peso_inicial CHECK (peso_inicial_g > 0);

ALTER TABLE conteos
    ADD CONSTRAINT ck_conteos_total CHECK (total >= 0);
ALTER TABLE conteos
    ADD CONSTRAINT ck_conteos_cortes CHECK (cortes_multiples >= 0);

-- Una mortalidad de cero peces no es un dato, es ruido en la cola del celular.
ALTER TABLE mortalidades
    ADD CONSTRAINT ck_mortalidades_cantidad CHECK (cantidad > 0);

ALTER TABLE alimentaciones
    ADD CONSTRAINT ck_alimentaciones_kg CHECK (kg > 0 AND kg <= 1000);

ALTER TABLE biometrias
    ADD CONSTRAINT ck_biometrias_peso CHECK (peso_promedio_g > 0 AND peso_promedio_g <= 5000);
ALTER TABLE biometrias
    ADD CONSTRAINT ck_biometrias_muestra CHECK (muestra >= 1);
