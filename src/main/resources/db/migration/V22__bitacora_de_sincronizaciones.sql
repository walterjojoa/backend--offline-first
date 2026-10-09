-- El push devuelve al celular cuantos eventos acepto, cuantos eran repetidos y cuales
-- rechazo, pero en el servidor no quedaba nada: esa respuesta se iba con el HTTP y se perdia.
--
-- Para el proyecto esta es la evidencia de que el esquema offline-first funciona: los
-- duplicados demuestran que los reintentos son seguros, los rechazos muestran que un dato
-- malo no frena la cola, y la frecuencia de los envios muestra como se comporta la finca
-- cuando no hay señal. Tambien es lo primero que uno mira cuando algo no cuadra.
CREATE TABLE sincronizaciones (
    id                UUID PRIMARY KEY,
    dispositivo_id    VARCHAR(64)              NOT NULL REFERENCES dispositivos (id),
    -- Estanques y lotes guardados mas eventos nuevos.
    aceptados         INTEGER                  NOT NULL,
    -- Eventos que el celular ya habia subido: cada uno es un reintento que no duplico datos.
    duplicados        INTEGER                  NOT NULL,
    -- Cambios de catalogo que llegaron viejos y perdieron contra uno mas reciente.
    obsoletos         INTEGER                  NOT NULL,
    rechazados        INTEGER                  NOT NULL,
    alertas_generadas INTEGER                  NOT NULL,
    servidor_en       TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX ix_sincronizaciones_dispositivo_fecha
    ON sincronizaciones (dispositivo_id, servidor_en DESC);

ALTER TABLE sincronizaciones ADD CONSTRAINT ck_sincronizaciones_conteos CHECK (
    aceptados >= 0 AND duplicados >= 0 AND obsoletos >= 0
    AND rechazados >= 0 AND alertas_generadas >= 0
);
