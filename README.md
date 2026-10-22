# Sistema Distribuido de Reserva de Boletos (SRB) - Primera entrega

Proyecto de Introducción a Sistemas Distribuidos (Pontificia Universidad Javeriana, 2026-30).
Estudiantes: Alejandro Martínez Mesa, Julian Rojas Almanza y Samuel Freile Avendaño.

## Resumen del despliegue

Todos los comandos se ejecutan desde la carpeta del proyecto, que está compartida por NFS en las tres máquinas.

| Paso | Dónde | Comando |
|------|-------|---------|
| 1. IP de las máquinas 2 y 3 | Cualquiera | `nano deploy/.env` |
| 2. Permiso de la carpeta de métricas | Cualquiera | `chmod 777 resultados` |
| 3. Almacenamiento | Máquina 3 | `docker compose -f deploy/maquina3-almacenamiento.yml up -d --build` |
| 4. Negocio | Máquina 2 | `docker compose -f deploy/maquina2-negocio.yml up -d --build` |
| 5. Página web | Máquina 1 | `docker compose -f deploy/maquina1-consumidores.yml up -d --build web` |
| 6. Acceso a la página | Navegador | `http://IP_DE_LA_MAQUINA_1:8090` |
| 7. Protocolo de pruebas | Máquina 1 | `bash scripts/protocolo-pruebas.sh` |

Las secciones 4 y 5 detallan cada paso.

## 1. Qué hace

Permite **consultar** los eventos de un mes y **reservar** asientos, desde una página web o con clientes que generan
carga. Los datos se guardan en PostgreSQL. Modificar y cancelar reservas son de la segunda entrega, y todavía **no hay
tolerancia a fallas**: si un servicio se cae, no se reemplaza solo.

## 2. Las tres máquinas

Cada capa de la arquitectura corre en su propia máquina. La carpeta del proyecto está compartida por NFS, así que las
tres ven los mismos archivos.

| Máquina | Capa | Qué corre | Archivo | Puertos abiertos |
|---------|------|-----------|---------|------------------|
| 1 | Consumidores | Página web y clientes (generador de carga y ejecutor de casos) | `deploy/maquina1-consumidores.yml` | 8090 (página web) |
| 2 | Negocio | Gestor de Transacciones y Servicio de Procesamiento de Reservas, Modificaciones y Consultas | `deploy/maquina2-negocio.yml` | 5555 hacia la máquina 1 |
| 3 | Almacenamiento | Servicio de Persistencia y PostgreSQL | `deploy/maquina3-almacenamiento.yml` | 5557 hacia la máquina 2 y 5432 hacia la máquina 1 |

Las solicitudes viajan así: máquina 1 → máquina 2 (puerto 5555) → máquina 3 (puerto 5557) → PostgreSQL. El puerto 5432
de la máquina 3 solo se usa desde la máquina 1 para el protocolo de pruebas.

## 3. Antes de empezar

- Las tres máquinas tienen Linux, Docker y Docker Compose, y están conectadas por la red.
- La carpeta del proyecto está montada por NFS en las tres. **Todos los comandos se ejecutan desde esa carpeta.**
- Si el usuario no está en el grupo `docker`, los comandos `docker` llevan `sudo` al inicio.

## 4. Configuración (una sola vez, desde cualquier máquina)

**4.1 IP de las máquinas.** La IP de cada máquina aparece con este comando, ejecutado en ella:

```bash
hostname -I
```

El primer número que aparece (por ejemplo `10.0.0.20`) es su IP. Las IP de las máquinas 2 y 3 se escriben en
`deploy/.env`, una por línea, sin espacios ni comillas:

```bash
nano deploy/.env
```

```
IP_NEGOCIO=10.0.0.20
IP_ALMACENAMIENTO=10.0.0.30
```

- `IP_NEGOCIO` es la IP de la **máquina 2**; la usa la máquina 1.
- `IP_ALMACENAMIENTO` es la IP de la **máquina 3**; la usan la máquina 2 y el protocolo de pruebas.

Solo hacen falta dos IP porque cada máquina únicamente necesita saber dónde está la siguiente capa. La IP de la máquina
1 solo se usa para abrir la página en el navegador. Como la carpeta está en NFS, el archivo se edita una sola vez y lo
leen las tres máquinas.

**4.2 Carpeta de métricas.** La carpeta `resultados/` ya viene en el proyecto; ahí el generador de carga guarda sus
resultados. Necesita permiso de escritura para todos porque en NFS el usuario root de los contenedores no puede escribir
por defecto:

```bash
chmod 777 resultados
```

**4.3 Puertos.** En las máquinas con el firewall activo (`sudo ufw status` muestra `active`), los puertos se abren así:

```bash
sudo ufw allow 5557/tcp && sudo ufw allow 5432/tcp    # en la máquina 3
sudo ufw allow 5555/tcp                               # en la máquina 2
sudo ufw allow 8090/tcp                               # en la máquina 1
```

## 5. Despliegue (en este orden: 3, 2, 1)

El orden importa porque cada máquina se conecta a la que se levantó antes. La primera vez, cada máquina tarda unos
minutos porque compila el proyecto; las siguientes veces es casi inmediato.

**5.1 Máquina 3 - almacenamiento:**

```bash
docker compose -f deploy/maquina3-almacenamiento.yml up -d --build
```

Arranca PostgreSQL, que en el primer arranque crea las tablas y los datos iniciales, y el Servicio de Persistencia. Los
datos quedan en un volumen de Docker en el disco local de la máquina 3, no en la carpeta NFS (una base de datos sobre NFS
puede dañarse).

**5.2 Máquina 2 - negocio:**

```bash
docker compose -f deploy/maquina2-negocio.yml up -d --build
```

Arranca el Servicio de Procesamiento, que se conecta a la máquina 3, y el Gestor de Transacciones.

**5.3 Máquina 1 - consumidores:**

```bash
docker compose -f deploy/maquina1-consumidores.yml up -d --build web
```

Arranca la página web, que queda en `http://IP_DE_LA_MAQUINA_1:8090` (con la IP que muestra `hostname -I` en la
máquina 1).

**5.4 Comprobación.** En cada máquina, su archivo muestra los servicios y su estado:

```bash
docker compose -f deploy/maquina3-almacenamiento.yml ps     # en la máquina 3
docker compose -f deploy/maquina2-negocio.yml ps            # en la máquina 2
docker compose -f deploy/maquina1-consumidores.yml ps       # en la máquina 1
```

En un despliegue correcto, cada servicio aparece como `Up` (y `postgres`, además, como `healthy`):

| Máquina | Servicios que deben aparecer |
|---------|------------------------------|
| 3 | `postgres`, `persistencia` |
| 2 | `procesamiento`, `gestor-transacciones` |
| 1 | `web` |

**5.5 Prueba de punta a punta** (en la máquina 1). Envía una consulta que atraviesa las tres máquinas:

```bash
docker compose -f deploy/maquina1-consumidores.yml run --rm clientes java -cp srb.jar srb.clientes.Cliente consulta 2026-10
```

Una respuesta que empieza con `"ok": true` y trae los eventos confirma que las tres máquinas están conectadas. Una
respuesta `Sin respuesta de tcp://...` indica un problema de conexión (sección 11).

## 6. Uso del sistema

### 6.1 Página web (máquina 1)

La página está en `http://IP_DE_LA_MAQUINA_1:8090` y tiene estas secciones:

- **Eventos:** consulta los eventos del mes y reserva asientos con el botón «Reservar».
- **Mis reservas:** reservas confirmadas desde la página para el cliente escrito en el menú.
- **Estado de la base de datos:** totales y ocupación de cada ocurrencia; «Guardar antes» permite comparar antes y
  después.
- **Registro de operaciones:** cada solicitud enviada al Gestor de Transacciones, con su resultado y tiempo.
- **Modificar, Cancelar y Notificaciones:** en construcción (segunda entrega).

### 6.2 Bitácoras de cada proceso

Cada servicio escribe en su bitácora qué recibió, qué hizo y el resultado. El mismo `id` aparece en todas las máquinas,
así se sigue una solicitud de punta a punta. `Ctrl+C` cierra la bitácora sin detener los servicios.

```bash
docker compose -f deploy/maquina2-negocio.yml logs -f gestor-transacciones procesamiento   # en la máquina 2
docker compose -f deploy/maquina3-almacenamiento.yml logs -f persistencia                   # en la máquina 3
docker compose -f deploy/maquina1-consumidores.yml logs -f web                              # en la máquina 1
```

### 6.3 Ver y reiniciar la base de datos (máquina 3)

```bash
docker compose -f deploy/maquina3-almacenamiento.yml exec postgres psql -U srb -d srb -c "SELECT * FROM estado_ocurrencias WHERE ocupados > 0"
docker compose -f deploy/maquina3-almacenamiento.yml exec postgres psql -U srb -d srb -c "SELECT * FROM reservas ORDER BY id"
docker compose -f deploy/maquina3-almacenamiento.yml exec postgres sh -c "psql -q -U srb -d srb -f /docker-entrypoint-initdb.d/init.sql"
```

El primero muestra las ocurrencias con asientos ocupados, el segundo las reservas y el tercero devuelve la base de datos a
su estado inicial sin detener nada.

### 6.4 Datos iniciales

`db/init.sql` crea 50 eventos (E1 a E50) en octubre de 2026. E1 a E20 se repiten dos veces en el mes (70 ocurrencias en
total, numeradas del 1 al 70), cada una con 100 asientos. Otro catálogo se carga editando ese archivo y reiniciando la
base de datos con el tercer comando de la sección 6.3.

### 6.5 Solicitudes manuales (máquina 1)

```bash
docker compose -f deploy/maquina1-consumidores.yml run --rm clientes java -cp srb.jar srb.clientes.Cliente consulta 2026-10
docker compose -f deploy/maquina1-consumidores.yml run --rm clientes java -cp srb.jar srb.clientes.Cliente reserva ana 5 2
```

La consulta recibe el mes (`AAAA-MM`). La reserva recibe cliente, ocurrencia y asientos: el ejemplo reserva 2 asientos
de la ocurrencia 5 a nombre de `ana`.

### 6.6 Caso de prueba con operaciones fijas (máquina 1)

Las operaciones van en un CSV dentro de `casos/`, una por línea, con las columnas
`cliente,operacion,mes,ocurrencia,asientos`. Las columnas que una operación no usa se dejan vacías:

```
cliente,operacion,mes,ocurrencia,asientos
ana,consulta,2026-10,,
ana,reserva,,1,2
```

Se envían en orden, una por una, así la base de datos termina siempre igual. Ejecución de `casos/ejemplo.csv`:

```bash
docker compose -f deploy/maquina1-consumidores.yml run --rm clientes java -cp srb.jar srb.clientes.EjecutorCaso casos/ejemplo.csv
```

## 7. Generación de carga desde los clientes (máquina 1)

**Método:** un programa propio en Java, `GeneradorCarga`. Cada cliente es un hilo que envía solicitudes al Gestor de
Transacciones a ritmo fijo: una cada 60 / R segundos, donde R son las solicitudes por minuto. Los clientes «solo
consulta» envían únicamente consultas; los demás mezclan consultas y reservas (de 1 a 3 asientos en una ocurrencia al
azar) según un porcentaje. No se usaron Locust ni JMeter porque están hechos para HTTP y el sistema usa ZeroMQ.

Los parámetros se pasan con `-e NOMBRE=valor` en cada corrida; los que no se pasan toman el valor por defecto:

| Variable | Por defecto | Significado |
|----------|-------------|-------------|
| `CLIENTES` | 3 | Clientes simultáneos (un hilo cada uno) |
| `CLIENTES_SOLO_CONSULTA` | 2 | Cuántos de ellos solo consultan |
| `SOLICITUDES_POR_MINUTO` | 20 | Solicitudes de cada cliente por minuto (el enunciado pide al menos 20) |
| `PORCENTAJE_CONSULTA` | 50 | En los clientes mixtos, porcentaje de consultas (el resto son reservas) |
| `DURACION_SEGUNDOS` | 120 | Duración de la prueba (el enunciado pide 2 a 3 minutos) |

Con los valores por defecto:

```bash
docker compose -f deploy/maquina1-consumidores.yml run --rm clientes
```

Los tres escenarios de la Tabla 1 del enunciado (cada uno dura 150 segundos):

```bash
docker compose -f deploy/maquina1-consumidores.yml run --rm -e CLIENTES=3 -e CLIENTES_SOLO_CONSULTA=2 -e DURACION_SEGUNDOS=150 clientes
docker compose -f deploy/maquina1-consumidores.yml run --rm -e CLIENTES=5 -e CLIENTES_SOLO_CONSULTA=3 -e DURACION_SEGUNDOS=150 clientes
docker compose -f deploy/maquina1-consumidores.yml run --rm -e CLIENTES=8 -e CLIENTES_SOLO_CONSULTA=4 -e DURACION_SEGUNDOS=150 clientes
```

## 8. Métricas de desempeño (Tabla 1)

| Métrica | Cómo se obtiene | Herramienta |
|---------|-----------------|-------------|
| Tiempo promedio de respuesta | Hora de recepción menos hora de envío de cada solicitud | `System.nanoTime()` de Java, en `GeneradorCarga` |
| Desviación estándar | Desviación muestral de esos tiempos | Calculada en `GeneradorCarga` |
| Transacciones por minuto (consulta y reserva) | Respuestas recibidas × 60 / duración en segundos | `GeneradorCarga`, y la bitácora del Gestor de Transacciones, que cada minuto escribe cuántas atendió |
| Uso de CPU y memoria (apoyo) | Consumo de cada contenedor durante la prueba | `docker stats`, en cada máquina |

Al terminar, el generador muestra los resultados y agrega dos filas (una para consulta y otra para reserva) a
`resultados/metricas.csv`, con las columnas
`fecha,arquitectura,clientes,operacion,procesadas,exitosas,promedio_ms,desviacion_ms,transacciones_por_minuto`:

```bash
cat resultados/metricas.csv
```

Las transacciones por minuto medidas en el gestor se ven en la máquina 2:

```bash
docker compose -f deploy/maquina2-negocio.yml logs gestor-transacciones | grep "por minuto"
```

**Estrategia:** antes de cada corrida se reinicia la base de datos; cada escenario dura 2 a 3 minutos y se repite tres
veces para promediar; todas las corridas se hacen en las mismas máquinas, cuyas características se anotan. Con el CSV
se arman la tabla y los gráficos que comparan la arquitectura base con la escalable (segunda entrega).

## 9. Protocolo de pruebas

| Tipo de prueba | Qué comprueba | Cómo / herramienta | Estado |
|----------------|---------------|--------------------|--------|
| Unitarias | Validación del gestor, lectura de mensajes, cálculo de métricas, lectura del CSV | JUnit 5 (46 pruebas) | Hecha |
| Integración | Base de datos real y sistema completo por ZeroMQ | JUnit 5 (13 pruebas) | Hecha |
| Funcionales y de aceptación | Consulta y reserva según el enunciado; la base final coincide con el caso de prueba | `EjecutorCaso` y página web | Hecha |
| Concurrencia | Varias reservas a la vez nunca superan los 100 asientos | JUnit 5 con varios hilos | Hecha |
| Validación de entradas | Mes mal escrito, cliente vacío, valores negativos u ocurrencia inexistente se rechazan sin cambios | JUnit 5 | Hecha |
| Despliegue | Funcionamiento con una capa en cada una de las tres máquinas | Archivos de `deploy/` | Final |
| Seguridad | Entradas maliciosas no alteran la base (consultas parametrizadas, solo los puertos necesarios abiertos) | Revisión y pruebas manuales | Final |
| Rendimiento y carga | Tiempos y transacciones por minuto con 3, 5 y 8 clientes | `GeneradorCarga` | Final |
| Estrés | Hasta dónde aguanta el sistema subiendo clientes y solicitudes por minuto | `GeneradorCarga` | Final |
| Escalabilidad | Mejora de la arquitectura escalable frente a la base | `GeneradorCarga` en ambas arquitecturas | Final |
| Tolerancia a fallas | Detener el gestor o el procesamiento en plena carga sin pérdidas ni duplicados | `docker stop` durante una carga | Final |
| Usabilidad | La página web se entiende y responde | Prueba con compañeros | Final |
| Regresión | Lo que ya funcionaba sigue funcionando tras cada cambio | Repetir todas las pruebas JUnit | Cada entrega |

**Ejecución del protocolo** (en la máquina 1, con las tres máquinas levantadas). Necesita `curl` en la máquina 1.
Muestra cada resultado y un resumen PASA / FALLA, tarda unos 2 minutos y al final deja la base de datos en su estado
inicial:

```bash
bash scripts/protocolo-pruebas.sh
DURACION_CARGA=60 bash scripts/protocolo-pruebas.sh      # con una carga más larga
```

Hace, en orden: 1) las pruebas unitarias JUnit; 2) las de integración JUnit contra la base de datos de la máquina 3 y el
Gestor de Transacciones de la máquina 2; 3) el caso `casos/ejemplo.csv`, comprobando la base de datos final; 4) siete
solicitudes inválidas, que deben rechazarse sin cambiar nada; 5) 30 reservas simultáneas de 5 asientos sobre una
ocurrencia de 100, de las que deben entrar exactamente 20; y 6) una carga corta con el generador.

Solo las pruebas unitarias, en cualquier máquina (el código se copia dentro del contenedor para no escribir en la
carpeta NFS):

```bash
docker run --rm -v "$PWD:/src:ro" maven:3.9-eclipse-temurin-21 sh -c "cp -r /src /app && cd /app && mvn test"
```

## 10. Detener

En orden inverso al despliegue (1, 2, 3):

```bash
docker compose -f deploy/maquina1-consumidores.yml down      # en la máquina 1
docker compose -f deploy/maquina2-negocio.yml down           # en la máquina 2
docker compose -f deploy/maquina3-almacenamiento.yml down    # en la máquina 3 (los datos se conservan)
docker compose -f deploy/maquina3-almacenamiento.yml down -v # en la máquina 3, borrando también la base de datos
```

Si cambia una IP en `deploy/.env`, se vuelve a ejecutar el `up` de la máquina que la usa: la máquina 1 si cambió
`IP_NEGOCIO` y la máquina 2 si cambió `IP_ALMACENAMIENTO`.

## 11. Problemas frecuentes

| Síntoma | Causa probable | Corrección |
|---------|----------------|------------|
| `Falta IP_NEGOCIO en deploy/.env` (o `IP_ALMACENAMIENTO`) | El archivo no tiene esa línea o está mal escrita | Se completa la línea en `deploy/.env` (sección 4.1) |
| `compose file "/var/lib/snapd/void/..." is invalid: ... no such file or directory` | Docker está instalado con `snap`, que no puede leer carpetas fuera de `/home` (como el montaje NFS) | Se usa Docker del repositorio oficial en lugar del de `snap` (`snap list docker` muestra si viene de `snap`) |
| `permission denied while trying to connect to the Docker daemon` | El usuario no está en el grupo `docker` | El comando se ejecuta con `sudo` |
| La página web no abre en el navegador | El servicio `web` no está arriba o el puerto 8090 está cerrado | Se revisan el estado del servicio (sección 5.4) y el puerto (sección 4.3) en la máquina 1 |
| La página abre, pero las consultas dan `Sin respuesta de tcp://...:5555` | La máquina 2 no está arriba, `IP_NEGOCIO` está mal o el puerto 5555 está cerrado | Se revisan la máquina 2, `deploy/.env` y el puerto, y luego se repite el `up` de la máquina 1 |
| Las consultas dan `Sin respuesta de tcp://...:5557` | La máquina 3 no está arriba, `IP_ALMACENAMIENTO` está mal o el puerto 5557 está cerrado | Se revisan la máquina 3, `deploy/.env` y el puerto, y luego se repite el `up` de la máquina 2 |
| `port is already allocated` al levantar | Otro programa usa ese puerto en la máquina | Se detiene el programa que ocupa el puerto |
| El generador de carga no puede escribir `resultados/metricas.csv` | Falta el permiso de la carpeta | Se da el permiso de la sección 4.2 |
| El protocolo da `No hay conexión con PostgreSQL` | El puerto 5432 de la máquina 3 está cerrado | Se abre el puerto como en la sección 4.3 |

## 12. Estructura

```
src/main/java/srb/        código Java (un paquete por servicio)
src/main/resources/web/   página web (HTML, CSS y JavaScript)
src/test/java/srb/        pruebas JUnit
db/init.sql               tablas y datos iniciales de PostgreSQL
casos/                    casos de prueba en CSV
deploy/                   Docker Compose de cada máquina y .env con las IP
scripts/                  protocolo de pruebas
docs/informe/             informe de la primera entrega (PDF)
```
