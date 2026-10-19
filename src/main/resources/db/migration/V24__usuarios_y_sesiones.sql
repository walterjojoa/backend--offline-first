-- Hasta aqui la API se protegia con una sola clave compartida (X-API-Key): todos los celulares
-- mandaban la misma. Servia para el piloto, pero no dice quien hizo cada cosa y no se le puede
-- quitar el acceso a una persona sin cambiarsela a toda la finca.
--
-- Estas dos tablas agregan personas. El dispositivo sigue existiendo y sigue siendo el que firma
-- los eventos: un celular es un aparato y un usuario es una persona, y en la finca se prestan los
-- celulares. Por eso la sesion apunta a los dos.

CREATE TABLE usuarios (
    id                UUID PRIMARY KEY,
    -- El numero de documento es el que la gente de la finca se sabe de memoria. No se pide correo:
    -- varios cuidadores no tienen uno y obligarlos a inventarse uno ensucia el dato.
    documento         VARCHAR(32)              NOT NULL UNIQUE,
    nombre            VARCHAR(120)             NOT NULL,
    -- Hash BCrypt, 60 caracteres. La contrasena en limpio no entra nunca a la base.
    clave_hash        VARCHAR(72)              NOT NULL,
    -- cuidador: registra en campo. productor: ademas ve el panel y da de baja dispositivos.
    rol               VARCHAR(20)              NOT NULL DEFAULT 'cuidador',
    -- false = no puede entrar. No se borra: sus registros siguen apuntando a el.
    activo            BOOLEAN                  NOT NULL DEFAULT TRUE,
    creado_en         TIMESTAMP WITH TIME ZONE NOT NULL,
    ultimo_ingreso_en TIMESTAMP WITH TIME ZONE
);

ALTER TABLE usuarios ADD CONSTRAINT ck_usuarios_rol CHECK (rol IN ('cuidador', 'productor'));

-- El documento se consulta en cada ingreso y ademas se compara sin distinguir mayusculas.
CREATE INDEX ix_usuarios_documento ON usuarios (documento);

-- Una fila por cada ingreso que sigue vivo. Se eligio tabla y no un token firmado (JWT) porque
-- en esta finca lo que de verdad pasa es que se pierde un celular: con la tabla se cierra esa
-- sesion y ya; un token firmado seguiria sirviendo hasta que venza y no hay como revocarlo.
CREATE TABLE sesiones (
    id             UUID PRIMARY KEY,
    usuario_id     UUID                     NOT NULL REFERENCES usuarios (id),
    -- Se guarda el SHA-256 del token, no el token. Si alguien se lleva una copia de la base,
    -- lo que se lleva no sirve para entrar.
    token_hash     VARCHAR(64)              NOT NULL UNIQUE,
    -- Desde que aparato se abrio. Deja ver que una persona entro desde un celular prestado.
    dispositivo_id VARCHAR(64) REFERENCES dispositivos (id),
    creada_en      TIMESTAMP WITH TIME ZONE NOT NULL,
    -- La app funciona sin senal, asi que la sesion dura mucho: obligar a entrar cada semana
    -- dejaria al cuidador por fuera justo en la laguna. El celular la protege con un PIN local.
    expira_en      TIMESTAMP WITH TIME ZONE NOT NULL,
    -- Se lleno al cerrar sesion o al darla de baja. No se borra la fila: es la bitacora.
    cerrada_en     TIMESTAMP WITH TIME ZONE
);

CREATE INDEX ix_sesiones_usuario ON sesiones (usuario_id, creada_en DESC);

ALTER TABLE sesiones ADD CONSTRAINT ck_sesiones_vigencia CHECK (expira_en > creada_en);
