-- Los umbrales de calidad de agua estaban como constantes en servicio/Reglas.java. El README
-- dice que son "valores de referencia" que hay que ajustar con el productor y citar de AUNAP
-- y FAO, pero con el codigo compilado eso significaba recompilar y volver a desplegar para
-- mover un numero, y no quedaba registro de quien lo movio ni de donde salio.
--
-- Pasandolos a una tabla, el umbral es un dato: se cambia desde la base, queda con su fuente
-- al lado y la tesis puede mostrar de donde viene cada cifra.
CREATE TABLE parametros_rango (
    variable       VARCHAR(20) PRIMARY KEY,
    -- Como se nombra la variable en el mensaje de la alerta.
    nombre         VARCHAR(40)              NOT NULL,
    -- Si el nombre es femenino, para armar "Temperatura alta" y no "Temperatura alto".
    femenino       BOOLEAN                  NOT NULL,
    unidad         VARCHAR(10)              NOT NULL,
    -- Fuera del rango optimo se genera advertencia; fuera del critico, alerta critica.
    -- Los maximos admiten NULL para las variables sin tope por arriba, como el oxigeno:
    -- mas oxigeno no es un problema. Se usa NULL y no infinito porque PostgreSQL y H2 no
    -- tratan igual los valores infinitos.
    optimo_min     DOUBLE PRECISION         NOT NULL,
    optimo_max     DOUBLE PRECISION,
    critico_min    DOUBLE PRECISION         NOT NULL,
    critico_max    DOUBLE PRECISION,
    -- De donde salio el valor. Es la columna que vuelve defendible el sistema experto.
    fuente         VARCHAR(200)             NOT NULL,
    actualizado_en TIMESTAMP WITH TIME ZONE NOT NULL
);

-- El rango critico tiene que contener al optimo: si no, habria valores "criticos" que
-- ni siquiera salieron del rango optimo y nunca generarian alerta.
ALTER TABLE parametros_rango
    ADD CONSTRAINT ck_parametros_rango_coherente CHECK (
        critico_min <= optimo_min
        AND (optimo_max IS NULL OR optimo_min <= optimo_max)
        AND (critico_max IS NULL OR optimo_max IS NULL OR optimo_max <= critico_max)
    );
