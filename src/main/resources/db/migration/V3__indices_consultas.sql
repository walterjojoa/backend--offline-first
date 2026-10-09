-- Índices para las consultas más frecuentes del resumen y los historiales del lote.
CREATE INDEX ix_conteos_lote_registrado ON conteos (lote_id, registrado_en);
CREATE INDEX ix_mortalidades_lote_registrado ON mortalidades (lote_id, registrado_en);
CREATE INDEX ix_alimentaciones_lote_registrado ON alimentaciones (lote_id, registrado_en);
CREATE INDEX ix_biometrias_lote_registrado ON biometrias (lote_id, registrado_en);
CREATE INDEX ix_lecturas_agua_estanque_registrado ON lecturas_agua (estanque_id, registrado_en);
-- Para saber rápido si ya hay una alerta pendiente igual
CREATE INDEX ix_alertas_pendientes ON alertas (estanque_id, variable, nivel, atendida);
