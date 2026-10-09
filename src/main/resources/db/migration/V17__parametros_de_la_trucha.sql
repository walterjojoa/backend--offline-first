-- Los mismos valores que tenia Reglas.java, ahora como datos. Son para trucha arcoiris
-- (Oncorhynchus mykiss), que es lo que se cultiva en La Cocha.
--
-- La fuente dice explicitamente que estan sin citar, que es la verdad hoy: el README advierte
-- que hay que ajustarlos con el productor aliado y citarlos de AUNAP y FAO antes del piloto.
-- Dejarlo escrito en la base evita que alguien los tome por validados mas adelante.
INSERT INTO parametros_rango
    (variable, nombre, femenino, unidad, optimo_min, optimo_max, critico_min, critico_max, fuente, actualizado_en)
VALUES
    ('temp_c', 'Temperatura', true, ' °C', 10.0, 16.0, 6.0, 18.0,
     'Valor de referencia sin citar: ajustar con el productor y citar AUNAP/FAO antes del piloto', now()),
    ('ph', 'pH', false, '', 6.5, 8.5, 6.0, 9.0,
     'Valor de referencia sin citar: ajustar con el productor y citar AUNAP/FAO antes del piloto', now()),
    -- El oxigeno no tiene tope por arriba: mas oxigeno disuelto no perjudica al pez.
    ('oxigeno_mg_l', 'Oxígeno disuelto', false, ' mg/L', 6.0, NULL, 5.0, NULL,
     'Valor de referencia sin citar: ajustar con el productor y citar AUNAP/FAO antes del piloto', now());
