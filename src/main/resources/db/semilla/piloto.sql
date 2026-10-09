-- Datos de ejemplo para probar el panel y para la sustentacion: una finca con tres estanques
-- y dos meses de historia, suficiente para que el resumen del lote y las graficas muestren
-- algo real en vez de estar vacias.
--
-- NO es una migracion de Flyway a proposito. Si estuviera en db/migration, Flyway la correria
-- tambien en Neon y llenaria la base de produccion con datos inventados. Se carga a mano:
--
--   docker compose up -d
--   docker exec -i lacocha-postgres psql -U lacocha -d lacocha < src/main/resources/db/semilla/piloto.sql
--
-- Usa generate_series, que es de PostgreSQL. Para empezar de cero: docker compose down -v
--
-- Es idempotente: borra lo que haya sembrado antes (se reconoce por el dispositivo cel-demo
-- y por los nombres que empiezan en "Demo ") y lo vuelve a crear.

BEGIN;

DELETE FROM alertas          WHERE estanque_id IN (SELECT id FROM estanques WHERE nombre LIKE 'Demo %');
DELETE FROM lecturas_agua    WHERE dispositivo_id = 'cel-demo';
DELETE FROM conteos          WHERE dispositivo_id = 'cel-demo';
DELETE FROM mortalidades     WHERE dispositivo_id = 'cel-demo';
DELETE FROM alimentaciones   WHERE dispositivo_id = 'cel-demo';
DELETE FROM biometrias       WHERE dispositivo_id = 'cel-demo';
DELETE FROM sincronizaciones WHERE dispositivo_id = 'cel-demo';
DELETE FROM lotes            WHERE estanque_id IN (SELECT id FROM estanques WHERE nombre LIKE 'Demo %');
DELETE FROM estanques        WHERE nombre LIKE 'Demo %';
DELETE FROM dispositivos     WHERE id = 'cel-demo';

INSERT INTO dispositivos (id, descripcion, activo, primer_visto_en, ultimo_visto_en)
VALUES ('cel-demo', 'Celular de demostración', true, now() - interval '60 days', now());

INSERT INTO estanques (id, nombre, tipo, volumen_m3, activo, actualizado_en, servidor_en) VALUES
    ('aaaaaaaa-0000-0000-0000-000000000001', 'Demo Tanque 1',   'tanque',  18.0, true, now() - interval '60 days', now() - interval '60 days'),
    ('aaaaaaaa-0000-0000-0000-000000000002', 'Demo Tanque 2',   'tanque',  18.0, true, now() - interval '60 days', now() - interval '60 days'),
    ('aaaaaaaa-0000-0000-0000-000000000003', 'Demo Jaula Lago', 'jaula',  120.0, true, now() - interval '60 days', now() - interval '60 days');

INSERT INTO lotes (id, estanque_id, codigo, fecha_siembra, cantidad_inicial, peso_inicial_g, estado, fecha_cierre, actualizado_en, servidor_en) VALUES
    ('bbbbbbbb-0000-0000-0000-000000000001', 'aaaaaaaa-0000-0000-0000-000000000001', 'L-2026-01', CURRENT_DATE - 55, 5200, 1.8, 'activo',  NULL,              now(), now()),
    ('bbbbbbbb-0000-0000-0000-000000000002', 'aaaaaaaa-0000-0000-0000-000000000002', 'L-2026-02', CURRENT_DATE - 40, 4200, 2.1, 'activo',  NULL,              now(), now()),
    -- Un lote ya cosechado, para que el panel tenga un ciclo cerrado que mostrar.
    ('bbbbbbbb-0000-0000-0000-000000000003', 'aaaaaaaa-0000-0000-0000-000000000003', 'L-2025-11', CURRENT_DATE - 58, 8000, 1.5, 'cerrado', CURRENT_DATE - 10, now(), now());

-- Lecturas cada 4 horas durante 60 dias en los tres estanques. La temperatura oscila dentro
-- del rango bueno y el pH se mueve alrededor de 7.3, como un cultivo que va bien.
INSERT INTO lecturas_agua (id, estanque_id, temp_c, ph, oxigeno_mg_l, mv, dispositivo_id, origen, registrado_en, recibido_en)
SELECT gen_random_uuid(),
       e.id,
       round((13.0 + 2.0 * sin(h / 6.0))::numeric, 2)::double precision,
       round((7.3 + 0.4 * sin(h / 11.0))::numeric, 2)::double precision,
       round((7.0 + 0.8 * sin(h / 17.0))::numeric, 2)::double precision,
       1500 + (h % 40),
       'cel-demo', 'sensor',
       now() - (h * 4 || ' hours')::interval,
       now() - (h * 4 || ' hours')::interval
  FROM estanques e, generate_series(1, 60 * 6) AS s(h)
 WHERE e.nombre LIKE 'Demo %';

-- Dos episodios fuera de rango en el Tanque 1: un dia de calor y una caida de pH. Son los que
-- le dan a las alertas algo que mostrar.
INSERT INTO lecturas_agua (id, estanque_id, temp_c, ph, dispositivo_id, origen, registrado_en, recibido_en) VALUES
    ('cccccccc-0000-0000-0000-000000000001', 'aaaaaaaa-0000-0000-0000-000000000001', 19.4, 7.2, 'cel-demo', 'sensor', now() - interval '3 days',   now() - interval '3 days'),
    ('cccccccc-0000-0000-0000-000000000002', 'aaaaaaaa-0000-0000-0000-000000000001', 14.1, 6.2, 'cel-demo', 'sensor', now() - interval '26 hours', now() - interval '26 hours');

-- Las alertas normalmente las genera el servidor al recibir la lectura en el push. Aca se
-- insertan a mano porque la semilla entra por SQL y no pasa por Reglas; el mensaje y el nivel
-- son los mismos que produciria el sistema experto con los umbrales que carga V17.
INSERT INTO alertas (id, estanque_id, lectura_id, disparada_por, variable, valor, nivel, mensaje, medido_en, creada_en, atendida) VALUES
    (gen_random_uuid(), 'aaaaaaaa-0000-0000-0000-000000000001', 'cccccccc-0000-0000-0000-000000000001', 'lectura_agua', 'temp_c', 19.4, 'critica',
     'Temperatura alta: 19.4 °C (óptimo 10–16 °C)', now() - interval '3 days', now() - interval '3 days', false),
    (gen_random_uuid(), 'aaaaaaaa-0000-0000-0000-000000000001', 'cccccccc-0000-0000-0000-000000000002', 'lectura_agua', 'ph', 6.2, 'advertencia',
     'pH bajo: 6.2 (óptimo 6.5–8.5)', now() - interval '26 hours', now() - interval '26 hours', false);

-- Un conteo con el contador de alevinos al sembrar cada lote. Para L-2026-01 la factura decia
-- 5200 y la maquina conto 5140: esa diferencia entre lo facturado y lo recibido es justo lo
-- que el proyecto quiere poder medir, asi que la semilla la deja visible.
INSERT INTO conteos (id, lote_id, total, cortes_multiples, dispositivo_id, origen, registrado_en, recibido_en) VALUES
    (gen_random_uuid(), 'bbbbbbbb-0000-0000-0000-000000000001', 5140, 42, 'cel-demo', 'contador', now() - interval '55 days', now() - interval '55 days'),
    (gen_random_uuid(), 'bbbbbbbb-0000-0000-0000-000000000002', 4180, 31, 'cel-demo', 'contador', now() - interval '40 days', now() - interval '40 days'),
    (gen_random_uuid(), 'bbbbbbbb-0000-0000-0000-000000000003', 7890, 65, 'cel-demo', 'contador', now() - interval '58 days', now() - interval '58 days');

-- Mortalidad diaria baja, saltando algunos dias: la tabla no admite cantidad cero, porque
-- registrar cero muertos no es un dato.
INSERT INTO mortalidades (id, lote_id, cantidad, causa, dispositivo_id, origen, registrado_en, recibido_en)
SELECT gen_random_uuid(), 'bbbbbbbb-0000-0000-0000-000000000001', (d % 4) + 1,
       CASE WHEN d % 10 = 0 THEN 'manipulación' ELSE NULL END,
       'cel-demo', 'voz', now() - (d || ' days')::interval, now() - (d || ' days')::interval
  FROM generate_series(1, 54) AS s(d)
 WHERE d % 3 <> 0;

-- Alimentacion dos veces al dia del lote activo.
INSERT INTO alimentaciones (id, lote_id, kg, dispositivo_id, origen, registrado_en, recibido_en)
SELECT gen_random_uuid(), 'bbbbbbbb-0000-0000-0000-000000000001',
       round((0.25 + (54 - d) * 0.012)::numeric, 3)::double precision,
       'cel-demo', 'manual',
       now() - (d || ' days')::interval - (t || ' hours')::interval,
       now() - (d || ' days')::interval
  FROM generate_series(1, 54) AS s(d), (VALUES (7), (16)) AS horas(t);

-- Biometria cada dos semanas: el pez pasa de 1.8 g a cerca de 19 g.
INSERT INTO biometrias (id, lote_id, peso_promedio_g, muestra, dispositivo_id, origen, registrado_en, recibido_en)
SELECT gen_random_uuid(), 'bbbbbbbb-0000-0000-0000-000000000001',
       1.8 + (54 - d) * 0.33, 50, 'cel-demo', 'manual',
       now() - (d || ' days')::interval, now() - (d || ' days')::interval
  FROM (VALUES (54), (40), (26), (12), (2)) AS s(d);

COMMIT;

-- Para revisar que quedo:
--   select nombre, (select count(*) from lecturas_agua l where l.estanque_id = e.id) as lecturas
--     from estanques e where nombre like 'Demo %' order by nombre;
