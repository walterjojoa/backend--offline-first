-- V1 indexo alertas.atendida sola. Un indice sobre un booleano sirve de poco: tiene dos
-- valores, asi que cada uno apunta a media tabla y el motor suele preferir leerla completa.
-- Y a medida que el cuidador atiende alertas, la mitad util (atendida = false) se vuelve
-- una fraccion cada vez mas chica de un indice que sigue creciendo.
--
-- Las dos consultas que importan miran solo las alertas pendientes:
--   findTop100ByAtendidaFalseOrderByMedidoEnDesc    (las que baja el celular en el pull)
--   countByEstanqueIdAndAtendidaFalse               (el contador del resumen del lote)
--
-- Con indices parciales, el indice contiene unicamente las alertas pendientes: su tamaño
-- depende de cuantas quedan sin atender, no de cuantas se han generado en la historia.
DROP INDEX ix_alertas_atendida;

CREATE INDEX ix_alertas_pendientes
    ON alertas (medido_en DESC)
 WHERE atendida = false;

CREATE INDEX ix_alertas_pendientes_estanque
    ON alertas (estanque_id)
 WHERE atendida = false;

-- ix_alertas_estanque_id se conserva: GET /api/alertas?pendientes=false consulta el historial
-- completo de un estanque, y ahi los indices parciales no aplican.
