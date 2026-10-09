-- Hasta ahora dispositivo_id era texto libre repetido en las cinco tablas de eventos: no
-- habia forma de saber que celulares estan sincronizando, cuando fue la ultima vez que cada
-- uno subio su cola, ni de dar de baja uno perdido o robado.
--
-- El id es el mismo texto que ya mandan los eventos (no un UUID nuevo), para que los datos
-- que ya estan guardados puedan apuntar aca sin tener que reescribirlos.
CREATE TABLE dispositivos (
    id              VARCHAR(64) PRIMARY KEY,
    -- De quien es el celular, para que el productor sepa cual es cual ("celular de Don Luis").
    descripcion     VARCHAR(120),
    -- En false deja de poder sincronizar, para un celular perdido o que salio de la finca.
    activo          BOOLEAN                  NOT NULL,
    primer_visto_en TIMESTAMP WITH TIME ZONE NOT NULL,
    -- Se actualiza en cada push. Es la respuesta a "hace cuanto no sincroniza este celular",
    -- que en un sistema offline-first es la diferencia entre un equipo sin señal y uno perdido.
    ultimo_visto_en TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX ix_dispositivos_ultimo_visto_en ON dispositivos (ultimo_visto_en DESC);
