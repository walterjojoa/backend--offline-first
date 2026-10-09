-- Registra los dispositivos que ya aparecen en los eventos guardados antes de exigir la
-- llave foranea. Sin este paso la migracion fallaria en Neon sobre los datos existentes.
INSERT INTO dispositivos (id, activo, primer_visto_en, ultimo_visto_en)
SELECT vistos.dispositivo_id, true, MIN(vistos.recibido_en), MAX(vistos.recibido_en)
  FROM (SELECT dispositivo_id, recibido_en FROM lecturas_agua
        UNION ALL SELECT dispositivo_id, recibido_en FROM conteos
        UNION ALL SELECT dispositivo_id, recibido_en FROM mortalidades
        UNION ALL SELECT dispositivo_id, recibido_en FROM alimentaciones
        UNION ALL SELECT dispositivo_id, recibido_en FROM biometrias) vistos
 GROUP BY vistos.dispositivo_id;

-- Ahora dispositivo_id deja de ser texto libre: un evento solo puede venir de un dispositivo
-- que exista en la tabla. Un error de dedo en el id que manda la app ya no crea un
-- dispositivo fantasma al que queden colgados datos reales.
ALTER TABLE lecturas_agua
    ADD CONSTRAINT fk_lecturas_agua_dispositivo FOREIGN KEY (dispositivo_id) REFERENCES dispositivos (id);
ALTER TABLE conteos
    ADD CONSTRAINT fk_conteos_dispositivo FOREIGN KEY (dispositivo_id) REFERENCES dispositivos (id);
ALTER TABLE mortalidades
    ADD CONSTRAINT fk_mortalidades_dispositivo FOREIGN KEY (dispositivo_id) REFERENCES dispositivos (id);
ALTER TABLE alimentaciones
    ADD CONSTRAINT fk_alimentaciones_dispositivo FOREIGN KEY (dispositivo_id) REFERENCES dispositivos (id);
ALTER TABLE biometrias
    ADD CONSTRAINT fk_biometrias_dispositivo FOREIGN KEY (dispositivo_id) REFERENCES dispositivos (id);

-- No se indexa dispositivo_id en las tablas de eventos: ninguna consulta filtra por el y
-- serian cinco indices mas sobre las tablas que mas escrituras reciben. Si el panel llega a
-- necesitar "que subio este celular", se agrega ahi y no antes.
