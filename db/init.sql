-- Base de datos del SRB (PostgreSQL).
-- Docker ejecuta este archivo la primera vez que se crea el volumen de datos.
-- También sirve para reiniciar la base a su estado inicial (ver README).

DROP VIEW IF EXISTS estado_ocurrencias;
DROP TABLE IF EXISTS reservas;
DROP TABLE IF EXISTS ocurrencias;
DROP TABLE IF EXISTS eventos;

-- Un evento (E1, E2, ...) puede realizarse una o dos veces en el mes.
CREATE TABLE eventos (
    id     TEXT PRIMARY KEY,
    nombre TEXT NOT NULL
);

-- Cada realización de un evento en una fecha, con sus 100 asientos.
CREATE TABLE ocurrencias (
    id        SERIAL PRIMARY KEY,
    evento_id TEXT NOT NULL REFERENCES eventos(id),
    fecha     DATE NOT NULL,
    capacidad INTEGER NOT NULL DEFAULT 100,
    ocupados  INTEGER NOT NULL DEFAULT 0 CHECK (ocupados >= 0 AND ocupados <= capacidad)
);

-- Reservas hechas por los clientes.
CREATE TABLE reservas (
    id            SERIAL PRIMARY KEY,
    cliente       TEXT NOT NULL,
    ocurrencia_id INTEGER NOT NULL REFERENCES ocurrencias(id),
    asientos      INTEGER NOT NULL CHECK (asientos > 0),
    creada        TIMESTAMP NOT NULL DEFAULT now()
);

-- Datos iniciales: 50 eventos en octubre de 2026.
INSERT INTO eventos (id, nombre)
SELECT 'E' || n,
       (ARRAY['Concierto', 'Obra de teatro', 'Partido de fútbol', 'Conferencia', 'Cine al aire libre'])[1 + (n - 1) % 5] || ' ' || n
FROM generate_series(1, 50) AS n;

-- Primera ocurrencia de los 50 eventos (ocurrencias 1 a 50).
INSERT INTO ocurrencias (evento_id, fecha)
SELECT 'E' || n,
       CASE WHEN n <= 20 THEN DATE '2026-10-01' + (n - 1) % 14
            ELSE DATE '2026-10-01' + (n - 1) % 28 END
FROM generate_series(1, 50) AS n;

-- Los eventos E1 a E20 se repiten dos semanas después (ocurrencias 51 a 70).
INSERT INTO ocurrencias (evento_id, fecha)
SELECT 'E' || n, DATE '2026-10-15' + (n - 1) % 14
FROM generate_series(1, 20) AS n;

-- Vista para mostrar el estado de la base durante la sustentación.
CREATE VIEW estado_ocurrencias AS
SELECT o.id AS ocurrencia, e.id AS evento, e.nombre, o.fecha,
       o.capacidad, o.ocupados, o.capacidad - o.ocupados AS libres
FROM ocurrencias o JOIN eventos e ON e.id = o.evento_id
ORDER BY o.id;
