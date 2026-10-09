-- Los mismos numeros que estaban en Reglas.java, pasados a datos. Igual que con los umbrales,
-- la fuente dice que estan sin citar: la tasa por peso tiene que salir de la tabla del
-- fabricante del alimento que compre el productor, no de un valor generico.
INSERT INTO tasas_alimentacion (orden, peso_hasta_g, tasa_pct, fuente) VALUES
    (1, 1.0,  6.0, 'Valor de referencia sin citar: reemplazar por la tabla del fabricante del alimento'),
    (2, 5.0,  4.5, 'Valor de referencia sin citar: reemplazar por la tabla del fabricante del alimento'),
    (3, 20.0, 3.0, 'Valor de referencia sin citar: reemplazar por la tabla del fabricante del alimento'),
    (4, 50.0, 2.2, 'Valor de referencia sin citar: reemplazar por la tabla del fabricante del alimento'),
    -- Sin tope: de 50 g para arriba.
    (5, NULL, 1.5, 'Valor de referencia sin citar: reemplazar por la tabla del fabricante del alimento');

INSERT INTO factores_temperatura (orden, temp_hasta_c, factor, fuente) VALUES
    (1,  8.0, 0.5,  'Valor de referencia sin citar: ajustar con el productor y citar AUNAP/FAO'),
    (2, 10.0, 0.75, 'Valor de referencia sin citar: ajustar con el productor y citar AUNAP/FAO'),
    (3, 16.0, 1.0,  'Valor de referencia sin citar: ajustar con el productor y citar AUNAP/FAO'),
    (4, 18.0, 0.7,  'Valor de referencia sin citar: ajustar con el productor y citar AUNAP/FAO'),
    -- Sobre 18 °C se suspende la alimentacion: el factor queda en cero.
    (5, NULL, 0.0,  'Valor de referencia sin citar: ajustar con el productor y citar AUNAP/FAO');
