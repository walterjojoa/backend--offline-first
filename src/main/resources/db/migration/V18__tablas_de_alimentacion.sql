-- La racion diaria sale de dos tablas que tambien estaban compiladas en Reglas.java como
-- arrays de numeros: la tasa segun el peso del pez y un factor segun la temperatura del agua.
--
-- Se parte en dos tablas porque son dos cosas distintas: la tasa la publica el fabricante del
-- alimento y el factor depende de la biologia del pez. Se citan y se ajustan por separado.

-- Que porcentaje de la biomasa se da por dia segun cuanto pesa el pez.
CREATE TABLE tasas_alimentacion (
    -- El orden decide que fila se evalua primero. Se guarda explicito en vez de ordenar por
    -- peso_hasta_g porque la ultima fila lo tiene en NULL, y PostgreSQL y H2 no ordenan los
    -- NULL igual; de hecho la URL de H2 del proyecto trae DEFAULT_NULL_ORDERING por eso mismo.
    orden        INTEGER PRIMARY KEY,
    -- Peso maximo del pez al que aplica esta fila. NULL en la ultima: de ahi para arriba.
    peso_hasta_g DOUBLE PRECISION,
    tasa_pct     DOUBLE PRECISION NOT NULL,
    fuente       VARCHAR(200)     NOT NULL
);

ALTER TABLE tasas_alimentacion
    ADD CONSTRAINT ck_tasas_alimentacion_pct CHECK (tasa_pct >= 0 AND tasa_pct <= 100);
ALTER TABLE tasas_alimentacion
    ADD CONSTRAINT ck_tasas_alimentacion_peso CHECK (peso_hasta_g IS NULL OR peso_hasta_g > 0);

-- Cuanto se corrige esa tasa segun la temperatura del agua. Con el agua muy fria el pez come
-- menos, y pasado cierto punto se suspende la alimentacion.
CREATE TABLE factores_temperatura (
    orden        INTEGER PRIMARY KEY,
    temp_hasta_c DOUBLE PRECISION,
    -- 1.0 es la racion completa, 0.0 suspende la alimentacion.
    factor       DOUBLE PRECISION NOT NULL,
    fuente       VARCHAR(200)     NOT NULL
);

ALTER TABLE factores_temperatura
    ADD CONSTRAINT ck_factores_temperatura CHECK (factor >= 0 AND factor <= 2);
