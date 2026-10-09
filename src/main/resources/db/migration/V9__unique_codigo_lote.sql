-- El codigo del lote es como lo llama el productor en su cuaderno ("L12"). Puede repetirse
-- entre estanques distintos, pero no dentro del mismo: el resumen de /api/lotes/{id}/resumen
-- se reporta por codigo y dos lotes "L12" en el mismo tanque vuelven el reporte inutil.

-- Igual que con los estanques: conserva el mas antiguo y marca los demas con el inicio del id.
-- El codigo se recorta a 28 caracteres porque la columna es VARCHAR(40) y el sufijo ocupa 11.
UPDATE lotes
   SET codigo = SUBSTRING(codigo, 1, 28) || ' (' || SUBSTRING(CAST(id AS VARCHAR), 1, 8) || ')'
 WHERE EXISTS (
           SELECT 1
             FROM lotes otro
            WHERE otro.estanque_id = lotes.estanque_id
              AND otro.codigo = lotes.codigo
              AND (otro.servidor_en < lotes.servidor_en
                   OR (otro.servidor_en = lotes.servidor_en
                       AND CAST(otro.id AS VARCHAR) < CAST(lotes.id AS VARCHAR)))
       );

ALTER TABLE lotes
    ADD CONSTRAINT uq_lotes_estanque_codigo UNIQUE (estanque_id, codigo);
