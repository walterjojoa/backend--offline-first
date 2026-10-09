-- En PostgreSQL esta version reemplaza el indice del booleano atendida por dos indices
-- parciales que solo contienen las alertas pendientes. H2 no soporta indices parciales,
-- asi que aca se crean los compuestos equivalentes, poniendo atendida de primera para que
-- igual sirvan a las consultas de alertas pendientes.
DROP INDEX ix_alertas_atendida;

CREATE INDEX ix_alertas_pendientes ON alertas (atendida, medido_en DESC);

CREATE INDEX ix_alertas_pendientes_estanque ON alertas (atendida, estanque_id);
