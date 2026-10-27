-- Lo que encuentra el servicio de IA, guardado aparte de las alertas del sistema experto.
--
-- La tabla alertas ya creció una vez para esto: V20 le quito el NOT NULL a lectura_id y le agrego
-- lote_id y disparada_por, justo para que el sistema experto pudiera avisar de cosas que no son
-- del agua. Asi que la pregunta es legitima: por que no crecer otra vez en vez de abrir una tabla.
--
-- Por tres razones, y la tercera es la que decide:
--
--   1. ck_alertas_coherente exige hoy que una alerta de agua traiga su lectura y su variable, y
--      eso es un invariante de verdad del sistema experto: cada alerta suya sale de comparar UN
--      valor contra UN umbral, y poder exigirlo es lo que impide guardar una alerta huerfana. Un
--      hallazgo de la IA puede salir de la forma de treinta lecturas (combinacion), de la
--      pendiente de toda una ventana (tendencia) o de dos biometrias y la temperatura entre ellas
--      (crecimiento). Meterlo ahi obliga a relajar ese CHECK para todos, y se pierde la garantia
--      sobre las alertas que si la cumplian.
--
--   2. Un hallazgo trae puntaje, que es la medida que lo disparo, y alertas no tiene donde
--      ponerlo. Es la columna que la tesis necesita para ordenar los hallazgos y citarlos.
--
--   3. La tesis compara las dos cosas. La pregunta que tiene que poder responder es cuantas veces
--      la IA vio algo que el umbral no vio, y cuantas veces aviso de mas. Con los dos en la misma
--      tabla esa consulta depende de filtrar por disparada_por y de que nadie agregue un valor
--      nuevo sin acordarse; con dos tablas es contar filas. Lo que se mide se separa.
--
-- Y para el cuidador tambien son dos cosas distintas: un umbral cruzado es un hecho que verifica
-- bajando el sensor otra vez, y un hallazgo del modelo es una sospecha que vale la pena confirmar.
-- Presentarlos iguales le quita credibilidad al primero y se la regala al segundo.
--
-- Lo que SI comparten es el vocabulario de urgencia: nivel usa las mismas palabras que alertas
-- ('advertencia', 'critica') mas 'informe', que es el que agrega este servicio para lo que vale
-- saber pero no amerita caminar hasta el estanque.
--
-- El nivel lo pone este backend y no el servicio de IA, y vale decir por que. El servicio reporta
-- QUE encontro: una anomalia no trae urgencia, una tendencia trae cuantos dias faltan para el
-- limite, y un hallazgo del lote trae un booleano. Decidir que tan fuerte se dice cada cosa
-- depende de los umbrales de parametros_rango, que son datos de esta base y no del servicio, y de
-- a quien se le va a mostrar. Un servicio que no conoce los umbrales de esta finca no puede
-- graduar la urgencia de nada, asi que no lo intenta.
--
-- El mapeo esta en AiAnalysis.levelOf(), en una sola funcion para que se pueda leer de corrido.
CREATE TABLE hallazgos_ia (
    id          UUID PRIMARY KEY,
    -- Uno de los dos, nunca los dos ni ninguno: los hallazgos del agua son de un estanque y los
    -- del lote son de un lote. Lo exige ck_hallazgos_ia_de_uno_solo, mas abajo.
    estanque_id UUID                     REFERENCES estanques (id),
    lote_id     UUID                     REFERENCES lotes (id),
    -- De que ruta del servicio de IA salio. Son las cinco que ese servicio expone:
    --   anomalia     /anomalias            un valor raro para este estanque
    --   tendencia    /tendencias           hacia donde va y cuando cruzaria el limite
    --   combinacion  /combinaciones        una pareja de valores que este estanque nunca produjo
    --   crecimiento  /proyeccion-lote      el lote crece por debajo de lo que el agua permite
    --   mortalidad   /proyeccion-lote      se murieron mas de los esperados, o se acelero
    clase       VARCHAR(20)              NOT NULL,
    nivel       VARCHAR(15)              NOT NULL,
    -- La variable del agua cuando el hallazgo es de una sola; nulo cuando mira varias a la vez
    variable    VARCHAR(20),
    -- La medida que lo disparo, para poder ordenarlos y para que la tesis los pueda citar
    puntaje     DOUBLE PRECISION         NOT NULL,
    -- La frase que ve el cuidador, armada por el servicio y en espanol. Se guarda y no se vuelve
    -- a calcular: si manana se ajusta un umbral, el aviso de hoy tiene que seguir diciendo lo que
    -- dijo cuando se dio, que es lo que permite auditar por que se tomo una decision.
    mensaje     VARCHAR(400)             NOT NULL,
    -- Cuando se midio lo que lo disparo. Nulo en los hallazgos del lote, que miran un periodo.
    medido_en   TIMESTAMP WITH TIME ZONE,
    creado_en   TIMESTAMP WITH TIME ZONE NOT NULL,
    atendido    BOOLEAN                  NOT NULL,
    atendido_en TIMESTAMP WITH TIME ZONE,
    atendido_por VARCHAR(64)
);

ALTER TABLE hallazgos_ia
    ADD CONSTRAINT ck_hallazgos_ia_de_uno_solo CHECK (
        (estanque_id IS NOT NULL AND lote_id IS NULL)
        OR (estanque_id IS NULL AND lote_id IS NOT NULL)
    );

ALTER TABLE hallazgos_ia
    ADD CONSTRAINT ck_hallazgos_ia_nivel CHECK (nivel IN ('informe', 'advertencia', 'critica'));

-- Lo mismo que ya se exige en alertas: marcado como atendido tiene que decir cuando
ALTER TABLE hallazgos_ia
    ADD CONSTRAINT ck_hallazgos_ia_atendido_en
        CHECK (atendido = false OR atendido_en IS NOT NULL);

-- Para la pantalla: lo pendiente, lo mas nuevo primero
CREATE INDEX ix_hallazgos_ia_atendido ON hallazgos_ia (atendido, creado_en DESC);

-- Para no repetir el mismo aviso. El sensor mide cada pocos segundos y la tendencia del oxigeno
-- sigue siendo la misma tendencia en la siguiente lectura: sin esto, una bajada de una semana
-- dejaria mil hallazgos identicos y enterraria el que el cuidador todavia no ha atendido.
CREATE INDEX ix_hallazgos_ia_repetidos ON hallazgos_ia (estanque_id, clase, variable, atendido);
CREATE INDEX ix_hallazgos_ia_lote ON hallazgos_ia (lote_id, clase, atendido);
