#!/usr/bin/env bash
# Protocolo de pruebas de la primera entrega del SRB (Linux, tres máquinas).
# Se ejecuta en la MÁQUINA 1 con las tres máquinas ya levantadas. Usa las IP de deploy/.env.
# Ejecuta, en orden, las pruebas ya hechas y muestra un resumen PASA / FALLA:
#   1. Unitarias (JUnit)          2. Integración y concurrencia (JUnit)
#   3. Funcionales (caso CSV)     4. Validación de entradas
#   5. Concurrencia en vivo       6. Carga corta con el generador de carga
# Al terminar deja la base de datos en su estado inicial.
#
# Uso, desde la carpeta del proyecto:   bash scripts/protocolo-pruebas.sh
# Para una carga más larga (60 segundos):   DURACION_CARGA=60 bash scripts/protocolo-pruebas.sh

set -u
cd "$(dirname "$0")/.."
RAIZ="$(pwd)"

# ---------- Configuración: se lee de deploy/.env ----------
leer() { grep -E "^$1=" deploy/.env | tail -1 | cut -d= -f2- | tr -d '\r '; }
IP_NEGOCIO="${IP_NEGOCIO:-$(leer IP_NEGOCIO)}"
IP_ALMACENAMIENTO="${IP_ALMACENAMIENTO:-$(leer IP_ALMACENAMIENTO)}"
PUERTO_WEB="${PUERTO_WEB:-$(leer PUERTO_WEB)}"; PUERTO_WEB="${PUERTO_WEB:-8090}"
PUERTO_BD="${PUERTO_BD:-$(leer PUERTO_BD)}";     PUERTO_BD="${PUERTO_BD:-5432}"
DURACION_CARGA="${DURACION_CARGA:-30}"
WEB="http://localhost:$PUERTO_WEB"                     # la página web corre en esta máquina (la 1)
COMPOSE="docker compose -f deploy/maquina1-consumidores.yml"
MAVEN="maven:3.9-eclipse-temurin-21"

VERDE=$'\e[32m'; ROJO=$'\e[31m'; AZUL=$'\e[1;34m'; NORMAL=$'\e[0m'
RESUMEN=()
titulo()    { echo; echo "${AZUL}=== $1 ===${NORMAL}"; }
resultado() {  # resultado "nombre" código (0 = pasa)
    if [ "$2" -eq 0 ]; then echo "${VERDE}PASA${NORMAL}  $1"; RESUMEN+=("${VERDE}PASA${NORMAL}  $1")
    else echo "${ROJO}FALLA${NORMAL} $1"; RESUMEN+=("${ROJO}FALLA${NORMAL} $1"); fi
}
# Consultas a PostgreSQL de la máquina 3 (con un cliente psql dentro de Docker)
sql()       { docker run --rm -e PGPASSWORD=srb postgres:16-alpine psql -h "$IP_ALMACENAMIENTO" -p "$PUERTO_BD" -U srb -d srb -tAc "$1"; }
reiniciar() { docker run --rm -e PGPASSWORD=srb -v "$RAIZ/db:/db:ro" postgres:16-alpine psql -q -h "$IP_ALMACENAMIENTO" -p "$PUERTO_BD" -U srb -d srb -f /db/init.sql >/dev/null; }
reservar()  { curl -s -X POST "$WEB/api/reservas" -H "Content-Type: application/json" -d "$1"; }
rechazada() { echo "$1" | grep -q '"ok":false'; }

# ---------- 0. Comprobar que las tres máquinas responden ----------
titulo "Preparación"
echo "Máquina 2 (negocio): $IP_NEGOCIO   Máquina 3 (almacenamiento): $IP_ALMACENAMIENTO   Página web: $WEB"
if ! curl -s "$WEB/api/config" >/dev/null; then
    echo "${ROJO}La página web no responde en $WEB.${NORMAL} Las máquinas 3, 2 y 1 deben estar levantadas (ver README.md)."; exit 1
fi
if ! sql "SELECT 1" >/dev/null 2>&1; then
    echo "${ROJO}No hay conexión con PostgreSQL en $IP_ALMACENAMIENTO:$PUERTO_BD.${NORMAL} La máquina 3 debe estar levantada y con el puerto abierto."; exit 1
fi
if ! curl -s "$WEB/api/eventos?mes=2026-10" | grep -q '"ok":true'; then
    echo "${ROJO}La consulta no llega a la base de datos.${NORMAL} La máquina 2 debe estar levantada y las IP de deploy/.env deben ser correctas."; exit 1
fi
reiniciar
echo "Estado inicial: $(sql "SELECT count(*) FROM eventos") eventos, $(sql "SELECT count(*) FROM ocurrencias") ocurrencias," \
     "$(sql "SELECT count(*) FROM reservas") reservas, $(sql "SELECT sum(ocupados) FROM ocurrencias") asientos ocupados"

# ---------- 1. Pruebas unitarias (no necesitan las otras máquinas) ----------
titulo "1. Pruebas unitarias (JUnit 5)"
# El código se copia dentro del contenedor para no escribir en la carpeta del proyecto (NFS).
docker run --rm -v "$RAIZ:/src:ro" -v srb-maven:/root/.m2 "$MAVEN" \
    sh -c "cp -r /src /app && cd /app && mvn -B test" > /tmp/srb-unitarias.txt 2>&1
UNITARIAS=$?
grep -E "Tests run:|BUILD" /tmp/srb-unitarias.txt | tail -2
resultado "Unitarias: validación del gestor, mensajes, métricas y lectura del CSV" "$UNITARIAS"

# ---------- 2. Pruebas de integración (base de datos de la máquina 3 y gestor de la máquina 2) ----------
titulo "2. Pruebas de integración y concurrencia (JUnit 5 con la base de datos real)"
docker run --rm -v "$RAIZ:/src:ro" -v srb-maven:/root/.m2 \
    -e DB_URL="jdbc:postgresql://$IP_ALMACENAMIENTO:$PUERTO_BD/srb" -e DB_USUARIO=srb -e DB_CLAVE=srb \
    -e URL_GESTOR="tcp://$IP_NEGOCIO:5555" "$MAVEN" \
    sh -c "cp -r /src /app && cd /app && mvn -B test" > /tmp/srb-integracion.txt 2>&1
INTEGRACION=$?
grep -E "Tests run:|BUILD" /tmp/srb-integracion.txt | tail -2
resultado "Integración: consulta y reserva contra PostgreSQL, varias reservas a la vez y prueba de punta a punta" "$INTEGRACION"

# ---------- 3. Prueba funcional: caso de prueba en CSV ----------
titulo "3. Prueba funcional: caso casos/ejemplo.csv"
reiniciar
$COMPOSE run --rm -T clientes java -cp srb.jar srb.clientes.EjecutorCaso casos/ejemplo.csv 2>/dev/null | grep "\[CASO\]"
OCUPADOS_1=$(sql "SELECT ocupados FROM ocurrencias WHERE id = 1")
OCUPADOS_51=$(sql "SELECT ocupados FROM ocurrencias WHERE id = 51")
RESERVAS=$(sql "SELECT count(*) FROM reservas")
echo "Base de datos al final: ocurrencia 1 = $OCUPADOS_1 ocupados, ocurrencia 51 = $OCUPADOS_51 ocupados, $RESERVAS reservas"
[ "$OCUPADOS_1" = "100" ] && [ "$OCUPADOS_51" = "3" ] && [ "$RESERVAS" = "3" ]
resultado "Funcional: la base de datos final es la esperada (100 y 3 ocupados, 3 reservas)" $?

# ---------- 4. Validación de entradas ----------
titulo "4. Validación: solicitudes inválidas se rechazan sin cambiar la base de datos"
ANTES="$(sql "SELECT count(*) FROM reservas")/$(sql "SELECT sum(ocupados) FROM ocurrencias")"
FALLOS=0
probar() {  # probar "descripción" "respuesta"
    if rechazada "$2"; then echo "  rechazada como se esperaba: $1  ->  $(echo "$2" | sed 's/.*"error":"\([^"]*\)".*/\1/')"
    else echo "  ${ROJO}NO se rechazó${NORMAL}: $1  ->  $2"; FALLOS=$((FALLOS + 1)); fi
}
probar "mes mal escrito (2026-13)"      "$(curl -s "$WEB/api/eventos?mes=2026-13")"
probar "reserva sin cliente"            "$(reservar '{"cliente":"","ocurrencia":2,"asientos":1}')"
probar "asientos negativos"             "$(reservar '{"cliente":"ana","ocurrencia":2,"asientos":-2}')"
probar "asientos con letras"            "$(reservar '{"cliente":"ana","ocurrencia":2,"asientos":"dos"}')"
probar "ocurrencia que no existe (999)" "$(reservar '{"cliente":"ana","ocurrencia":999,"asientos":1}')"
probar "más asientos de los que hay"    "$(reservar '{"cliente":"ana","ocurrencia":2,"asientos":101}')"
probar "ocurrencia ya llena (1)"        "$(reservar '{"cliente":"ana","ocurrencia":1,"asientos":1}')"
DESPUES="$(sql "SELECT count(*) FROM reservas")/$(sql "SELECT sum(ocupados) FROM ocurrencias")"
echo "Reservas/asientos ocupados antes: $ANTES   después: $DESPUES"
[ "$FALLOS" -eq 0 ] && [ "$ANTES" = "$DESPUES" ]
resultado "Validación: las 7 solicitudes inválidas se rechazan y la base no cambia" $?

# ---------- 5. Concurrencia en vivo ----------
titulo "5. Concurrencia: 30 clientes piden 5 asientos al mismo tiempo de la ocurrencia 10 (100 asientos)"
RESPUESTAS=$(seq 1 30 | xargs -P 30 -I{} curl -s -X POST "$WEB/api/reservas" -H "Content-Type: application/json" \
    -d '{"cliente":"simultaneo-{}","ocurrencia":10,"asientos":5}')
ACEPTADAS=$(echo "$RESPUESTAS" | grep -o '"ok":true' | wc -l)
RECHAZADAS=$(echo "$RESPUESTAS" | grep -o '"ok":false' | wc -l)
OCUPADOS_10=$(sql "SELECT ocupados FROM ocurrencias WHERE id = 10")
echo "Aceptadas: $ACEPTADAS   Rechazadas: $RECHAZADAS   Ocupados en la ocurrencia 10: $OCUPADOS_10"
[ "$ACEPTADAS" -eq 20 ] && [ "$RECHAZADAS" -eq 10 ] && [ "$OCUPADOS_10" = "100" ]
resultado "Concurrencia: exactamente 20 reservas aceptadas y ninguna sobreventa" $?

# ---------- 6. Carga corta ----------
titulo "6. Carga: 3 clientes (2 solo consultan), 20 solicitudes por minuto, $DURACION_CARGA segundos"
reiniciar
$COMPOSE run --rm -T -e CLIENTES=3 -e CLIENTES_SOLO_CONSULTA=2 -e SOLICITUDES_POR_MINUTO=20 -e DURACION_SEGUNDOS="$DURACION_CARGA" clientes \
    2>/dev/null | grep "\[CARGA\]" | grep -v "guardadas"
RESERVADOS=$(sql "SELECT coalesce(sum(asientos), 0) FROM reservas")
OCUPADOS=$(sql "SELECT sum(ocupados) FROM ocurrencias")
echo "Asientos reservados: $RESERVADOS   Asientos ocupados: $OCUPADOS"
[ "$RESERVADOS" = "$OCUPADOS" ]
resultado "Carga: el sistema responde y los asientos reservados coinciden con los ocupados" $?

# ---------- Resumen ----------
reiniciar
titulo "Resumen del protocolo"
for linea in "${RESUMEN[@]}"; do echo "$linea"; done
echo
echo "Quedan para la entrega final: seguridad, rendimiento, estrés, escalabilidad, tolerancia a fallas,"
echo "usabilidad y regresión. La base de datos quedó en su estado inicial."
