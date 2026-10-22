================================================================================
  SISTEMA DISTRIBUIDO DE RESERVA DE BOLETOS (SRB) - PRIMERA ENTREGA
================================================================================

Proyecto de Introducción a Sistemas Distribuidos (Pontificia Universidad
Javeriana, 2026-30).
Estudiantes: Alejandro Martínez Mesa, Julian Rojas Almanza y Samuel Freile
Avendaño.

CONTENIDO
       Resumen del despliegue
   1. Qué hace
   2. Las tres máquinas
   3. Antes de empezar
   4. Configuración (una sola vez, desde cualquier máquina)
   5. Despliegue (en este orden: 3, 2, 1)
   6. Uso del sistema
   7. Generación de carga desde los clientes (máquina 1)
   8. Métricas de desempeño (Tabla 1)
   9. Protocolo de pruebas
  10. Detener
  11. Problemas frecuentes
  12. Estructura


--------------------------------------------------------------------------------
 RESUMEN DEL DESPLIEGUE
--------------------------------------------------------------------------------

Todos los comandos se ejecutan desde la carpeta del proyecto, que está
compartida por NFS en las tres máquinas.

  1. IP de las máquinas 2 y 3                   (cualquier máquina)
       nano deploy/.env

  2. Permiso de la carpeta de métricas          (cualquier máquina)
       chmod 777 resultados

  3. Almacenamiento                             (máquina 3)
       docker compose -f deploy/maquina3-almacenamiento.yml up -d --build

  4. Negocio                                    (máquina 2)
       docker compose -f deploy/maquina2-negocio.yml up -d --build

  5. Página web                                 (máquina 1)
       docker compose -f deploy/maquina1-consumidores.yml up -d --build web

  6. Acceso a la página                         (navegador)
       http://IP_DE_LA_MAQUINA_1:8090

  7. Protocolo de pruebas                       (máquina 1)
       bash scripts/protocolo-pruebas.sh

Las secciones 4 y 5 detallan cada paso.


--------------------------------------------------------------------------------
 1. QUÉ HACE
--------------------------------------------------------------------------------

Permite CONSULTAR los eventos de un mes y RESERVAR asientos, desde una página
web o con clientes que generan carga. Los datos se guardan en PostgreSQL.
Modificar y cancelar reservas son de la segunda entrega, y todavía NO HAY
TOLERANCIA A FALLAS: si un servicio se cae, no se reemplaza solo.


--------------------------------------------------------------------------------
 2. LAS TRES MÁQUINAS
--------------------------------------------------------------------------------

Cada capa de la arquitectura corre en su propia máquina. La carpeta del
proyecto está compartida por NFS, así que las tres ven los mismos archivos.

  MÁQUINA 1 - Consumidores
    Qué corre ........ Página web y clientes (generador de carga y ejecutor
                       de casos)
    Archivo .......... deploy/maquina1-consumidores.yml
    Puertos abiertos . 8090 (página web)

  MÁQUINA 2 - Negocio
    Qué corre ........ Gestor de Transacciones y Servicio de Procesamiento de
                       Reservas, Modificaciones y Consultas
    Archivo .......... deploy/maquina2-negocio.yml
    Puertos abiertos . 5555 hacia la máquina 1

  MÁQUINA 3 - Almacenamiento
    Qué corre ........ Servicio de Persistencia y PostgreSQL
    Archivo .......... deploy/maquina3-almacenamiento.yml
    Puertos abiertos . 5557 hacia la máquina 2 y 5432 hacia la máquina 1

Las solicitudes viajan así:

  máquina 1 --(5555)--> máquina 2 --(5557)--> máquina 3 --> PostgreSQL

El puerto 5432 de la máquina 3 solo se usa desde la máquina 1 para el
protocolo de pruebas.


--------------------------------------------------------------------------------
 3. ANTES DE EMPEZAR
--------------------------------------------------------------------------------

  - Las tres máquinas tienen Linux, Docker y Docker Compose, y están
    conectadas por la red.
  - La carpeta del proyecto está montada por NFS en las tres. TODOS LOS
    COMANDOS SE EJECUTAN DESDE ESA CARPETA.
  - Si el usuario no está en el grupo docker, los comandos docker llevan sudo
    al inicio.


--------------------------------------------------------------------------------
 4. CONFIGURACIÓN (una sola vez, desde cualquier máquina)
--------------------------------------------------------------------------------

4.1 IP DE LAS MÁQUINAS. La IP de cada máquina aparece con este comando,
    ejecutado en ella:

      hostname -I

    El primer número que aparece (por ejemplo 10.0.0.20) es su IP. Las IP de
    las máquinas 2 y 3 se escriben en deploy/.env, una por línea, sin
    espacios ni comillas:

      nano deploy/.env

      IP_NEGOCIO=10.0.0.20
      IP_ALMACENAMIENTO=10.0.0.30

    - IP_NEGOCIO es la IP de la MÁQUINA 2; la usa la máquina 1.
    - IP_ALMACENAMIENTO es la IP de la MÁQUINA 3; la usan la máquina 2 y el
      protocolo de pruebas.

    Solo hacen falta dos IP porque cada máquina únicamente necesita saber
    dónde está la siguiente capa. La IP de la máquina 1 solo se usa para
    abrir la página en el navegador. Como la carpeta está en NFS, el archivo
    se edita una sola vez y lo leen las tres máquinas.

4.2 CARPETA DE MÉTRICAS. La carpeta resultados/ ya viene en el proyecto; ahí
    el generador de carga guarda sus resultados. Necesita permiso de escritura
    para todos porque en NFS el usuario root de los contenedores no puede
    escribir por defecto:

      chmod 777 resultados

4.3 PUERTOS. En las máquinas con el firewall activo (sudo ufw status
    muestra active), los puertos se abren así:

      En la máquina 3:
        sudo ufw allow 5557/tcp && sudo ufw allow 5432/tcp
      En la máquina 2:
        sudo ufw allow 5555/tcp
      En la máquina 1:
        sudo ufw allow 8090/tcp


--------------------------------------------------------------------------------
 5. DESPLIEGUE (en este orden: 3, 2, 1)
--------------------------------------------------------------------------------

El orden importa porque cada máquina se conecta a la que se levantó antes. La
primera vez, cada máquina tarda unos minutos porque compila el proyecto; las
siguientes veces es casi inmediato.

5.1 MÁQUINA 3 - almacenamiento:

      docker compose -f deploy/maquina3-almacenamiento.yml up -d --build

    Arranca PostgreSQL, que en el primer arranque crea las tablas y los datos
    iniciales, y el Servicio de Persistencia. Los datos quedan en un volumen
    de Docker en el disco local de la máquina 3, no en la carpeta NFS (una
    base de datos sobre NFS puede dañarse).

5.2 MÁQUINA 2 - negocio:

      docker compose -f deploy/maquina2-negocio.yml up -d --build

    Arranca el Servicio de Procesamiento, que se conecta a la máquina 3, y el
    Gestor de Transacciones.

5.3 MÁQUINA 1 - consumidores:

      docker compose -f deploy/maquina1-consumidores.yml up -d --build web

    Arranca la página web, que queda en http://IP_DE_LA_MAQUINA_1:8090 (con
    la IP que muestra hostname -I en la máquina 1).

5.4 COMPROBACIÓN. En cada máquina, su archivo muestra los servicios y su
    estado:

      En la máquina 3:
        docker compose -f deploy/maquina3-almacenamiento.yml ps
      En la máquina 2:
        docker compose -f deploy/maquina2-negocio.yml ps
      En la máquina 1:
        docker compose -f deploy/maquina1-consumidores.yml ps

    En un despliegue correcto, cada servicio aparece como Up (y postgres,
    además, como healthy):

      Máquina 3 ... postgres, persistencia
      Máquina 2 ... procesamiento, gestor-transacciones
      Máquina 1 ... web

5.5 PRUEBA DE PUNTA A PUNTA (en la máquina 1). Envía una consulta que
    atraviesa las tres máquinas:

      docker compose -f deploy/maquina1-consumidores.yml run --rm clientes java -cp srb.jar srb.clientes.Cliente consulta 2026-10

    Una respuesta que empieza con "ok": true y trae los eventos confirma que
    las tres máquinas están conectadas. Una respuesta Sin respuesta de
    tcp://... indica un problema de conexión (sección 11).


--------------------------------------------------------------------------------
 6. USO DEL SISTEMA
--------------------------------------------------------------------------------

6.1 PÁGINA WEB (máquina 1)

    La página está en http://IP_DE_LA_MAQUINA_1:8090 y tiene estas
    secciones:

    - Eventos: consulta los eventos del mes y reserva asientos con el botón
      «Reservar».
    - Mis reservas: reservas confirmadas desde la página para el cliente
      escrito en el menú.
    - Estado de la base de datos: totales y ocupación de cada ocurrencia;
      «Guardar antes» permite comparar antes y después.
    - Registro de operaciones: cada solicitud enviada al Gestor de
      Transacciones, con su resultado y tiempo.
    - Modificar, Cancelar y Notificaciones: en construcción (segunda
      entrega).

6.2 BITÁCORAS DE CADA PROCESO

    Cada servicio escribe en su bitácora qué recibió, qué hizo y el
    resultado. El mismo id aparece en todas las máquinas, así se sigue una
    solicitud de punta a punta. Ctrl+C cierra la bitácora sin detener los
    servicios.

      En la máquina 2:
        docker compose -f deploy/maquina2-negocio.yml logs -f gestor-transacciones procesamiento
      En la máquina 3:
        docker compose -f deploy/maquina3-almacenamiento.yml logs -f persistencia
      En la máquina 1:
        docker compose -f deploy/maquina1-consumidores.yml logs -f web

6.3 VER Y REINICIAR LA BASE DE DATOS (máquina 3)

      Ocurrencias con asientos ocupados:
        docker compose -f deploy/maquina3-almacenamiento.yml exec postgres psql -U srb -d srb -c "SELECT * FROM estado_ocurrencias WHERE ocupados > 0"
      Reservas:
        docker compose -f deploy/maquina3-almacenamiento.yml exec postgres psql -U srb -d srb -c "SELECT * FROM reservas ORDER BY id"
      Devolver la base de datos a su estado inicial sin detener nada:
        docker compose -f deploy/maquina3-almacenamiento.yml exec postgres sh -c "psql -q -U srb -d srb -f /docker-entrypoint-initdb.d/init.sql"

6.4 DATOS INICIALES

    db/init.sql crea 50 eventos (E1 a E50) en octubre de 2026. E1 a E20 se
    repiten dos veces en el mes (70 ocurrencias en total, numeradas del 1 al
    70), cada una con 100 asientos. Otro catálogo se carga editando ese
    archivo y reiniciando la base de datos con el tercer comando de la
    sección 6.3.

6.5 SOLICITUDES MANUALES (máquina 1)

      docker compose -f deploy/maquina1-consumidores.yml run --rm clientes java -cp srb.jar srb.clientes.Cliente consulta 2026-10
      docker compose -f deploy/maquina1-consumidores.yml run --rm clientes java -cp srb.jar srb.clientes.Cliente reserva ana 5 2

    La consulta recibe el mes (AAAA-MM). La reserva recibe cliente,
    ocurrencia y asientos: el ejemplo reserva 2 asientos de la ocurrencia 5
    a nombre de ana.

6.6 CASO DE PRUEBA CON OPERACIONES FIJAS (máquina 1)

    Las operaciones van en un CSV dentro de casos/, una por línea, con las
    columnas cliente,operacion,mes,ocurrencia,asientos. Las columnas que una
    operación no usa se dejan vacías:

      cliente,operacion,mes,ocurrencia,asientos
      ana,consulta,2026-10,,
      ana,reserva,,1,2

    Se envían en orden, una por una, así la base de datos termina siempre
    igual. Ejecución de casos/ejemplo.csv:

      docker compose -f deploy/maquina1-consumidores.yml run --rm clientes java -cp srb.jar srb.clientes.EjecutorCaso casos/ejemplo.csv


--------------------------------------------------------------------------------
 7. GENERACIÓN DE CARGA DESDE LOS CLIENTES (máquina 1)
--------------------------------------------------------------------------------

MÉTODO: un programa propio en Java, GeneradorCarga. Cada cliente es un hilo
que envía solicitudes al Gestor de Transacciones a ritmo fijo: una cada 60 / R
segundos, donde R son las solicitudes por minuto. Los clientes «solo consulta»
envían únicamente consultas; los demás mezclan consultas y reservas (de 1 a 3
asientos en una ocurrencia al azar) según un porcentaje. No se usaron Locust
ni JMeter porque están hechos para HTTP y el sistema usa ZeroMQ.

Los parámetros se pasan con -e NOMBRE=valor en cada corrida; los que no se
pasan toman el valor por defecto:

  Variable                 Por defecto  Significado
  -----------------------  -----------  ---------------------------------------
  CLIENTES                 3            Clientes simultáneos (un hilo cada uno)
  CLIENTES_SOLO_CONSULTA   2            Cuántos de ellos solo consultan
  SOLICITUDES_POR_MINUTO   20           Solicitudes de cada cliente por minuto
                                        (el enunciado pide al menos 20)
  PORCENTAJE_CONSULTA      50           En los clientes mixtos, porcentaje de
                                        consultas (el resto son reservas)
  DURACION_SEGUNDOS        120          Duración de la prueba (el enunciado
                                        pide 2 a 3 minutos)

Con los valores por defecto:

  docker compose -f deploy/maquina1-consumidores.yml run --rm clientes

Los tres escenarios de la Tabla 1 del enunciado (cada uno dura 150 segundos):

  docker compose -f deploy/maquina1-consumidores.yml run --rm -e CLIENTES=3 -e CLIENTES_SOLO_CONSULTA=2 -e DURACION_SEGUNDOS=150 clientes
  docker compose -f deploy/maquina1-consumidores.yml run --rm -e CLIENTES=5 -e CLIENTES_SOLO_CONSULTA=3 -e DURACION_SEGUNDOS=150 clientes
  docker compose -f deploy/maquina1-consumidores.yml run --rm -e CLIENTES=8 -e CLIENTES_SOLO_CONSULTA=4 -e DURACION_SEGUNDOS=150 clientes


--------------------------------------------------------------------------------
 8. MÉTRICAS DE DESEMPEÑO (Tabla 1)
--------------------------------------------------------------------------------

  TIEMPO PROMEDIO DE RESPUESTA
    Cómo se obtiene: hora de recepción menos hora de envío de cada solicitud.
    Herramienta:     System.nanoTime() de Java, en GeneradorCarga.

  DESVIACIÓN ESTÁNDAR
    Cómo se obtiene: desviación muestral de esos tiempos.
    Herramienta:     calculada en GeneradorCarga.

  TRANSACCIONES POR MINUTO (consulta y reserva)
    Cómo se obtiene: respuestas recibidas x 60 / duración en segundos.
    Herramienta:     GeneradorCarga, y la bitácora del Gestor de
                     Transacciones, que cada minuto escribe cuántas atendió.

  USO DE CPU Y MEMORIA (apoyo)
    Cómo se obtiene: consumo de cada contenedor durante la prueba.
    Herramienta:     docker stats, en cada máquina.

Al terminar, el generador muestra los resultados y agrega dos filas (una para
consulta y otra para reserva) a resultados/metricas.csv, con las columnas
fecha,arquitectura,clientes,operacion,procesadas,exitosas,promedio_ms,
desviacion_ms,transacciones_por_minuto:

  cat resultados/metricas.csv

Las transacciones por minuto medidas en el gestor se ven en la máquina 2:

  docker compose -f deploy/maquina2-negocio.yml logs gestor-transacciones | grep "por minuto"

ESTRATEGIA: antes de cada corrida se reinicia la base de datos; cada escenario
dura 2 a 3 minutos y se repite tres veces para promediar; todas las corridas
se hacen en las mismas máquinas, cuyas características se anotan. Con el CSV
se arman la tabla y los gráficos que comparan la arquitectura base con la
escalable (segunda entrega).


--------------------------------------------------------------------------------
 9. PROTOCOLO DE PRUEBAS
--------------------------------------------------------------------------------

  [Hecha] UNITARIAS
      Qué comprueba: validación del gestor, lectura de mensajes, cálculo de
                     métricas, lectura del CSV.
      Cómo:          JUnit 5 (46 pruebas).

  [Hecha] INTEGRACIÓN
      Qué comprueba: base de datos real y sistema completo por ZeroMQ.
      Cómo:          JUnit 5 (13 pruebas).

  [Hecha] FUNCIONALES Y DE ACEPTACIÓN
      Qué comprueba: consulta y reserva según el enunciado; la base final
                     coincide con el caso de prueba.
      Cómo:          EjecutorCaso y página web.

  [Hecha] CONCURRENCIA
      Qué comprueba: varias reservas a la vez nunca superan los 100 asientos.
      Cómo:          JUnit 5 con varios hilos.

  [Hecha] VALIDACIÓN DE ENTRADAS
      Qué comprueba: mes mal escrito, cliente vacío, valores negativos u
                     ocurrencia inexistente se rechazan sin cambios.
      Cómo:          JUnit 5.

  [Final] DESPLIEGUE
      Qué comprueba: funcionamiento con una capa en cada una de las tres
                     máquinas.
      Cómo:          archivos de deploy/.

  [Final] SEGURIDAD
      Qué comprueba: entradas maliciosas no alteran la base (consultas
                     parametrizadas, solo los puertos necesarios abiertos).
      Cómo:          revisión y pruebas manuales.

  [Final] RENDIMIENTO Y CARGA
      Qué comprueba: tiempos y transacciones por minuto con 3, 5 y 8 clientes.
      Cómo:          GeneradorCarga.

  [Final] ESTRÉS
      Qué comprueba: hasta dónde aguanta el sistema subiendo clientes y
                     solicitudes por minuto.
      Cómo:          GeneradorCarga.

  [Final] ESCALABILIDAD
      Qué comprueba: mejora de la arquitectura escalable frente a la base.
      Cómo:          GeneradorCarga en ambas arquitecturas.

  [Final] TOLERANCIA A FALLAS
      Qué comprueba: detener el gestor o el procesamiento en plena carga sin
                     pérdidas ni duplicados.
      Cómo:          docker stop durante una carga.

  [Final] USABILIDAD
      Qué comprueba: la página web se entiende y responde.
      Cómo:          prueba con compañeros.

  [Cada entrega] REGRESIÓN
      Qué comprueba: lo que ya funcionaba sigue funcionando tras cada cambio.
      Cómo:          repetir todas las pruebas JUnit.

EJECUCIÓN DEL PROTOCOLO (en la máquina 1, con las tres máquinas levantadas).
Necesita curl en la máquina 1. Muestra cada resultado y un resumen
PASA / FALLA, tarda unos 2 minutos y al final deja la base de datos en su
estado inicial:

  bash scripts/protocolo-pruebas.sh

Con una carga más larga:

  DURACION_CARGA=60 bash scripts/protocolo-pruebas.sh

Hace, en orden:
  1) las pruebas unitarias JUnit;
  2) las de integración JUnit contra la base de datos de la máquina 3 y el
     Gestor de Transacciones de la máquina 2;
  3) el caso casos/ejemplo.csv, comprobando la base de datos final;
  4) siete solicitudes inválidas, que deben rechazarse sin cambiar nada;
  5) 30 reservas simultáneas de 5 asientos sobre una ocurrencia de 100, de
     las que deben entrar exactamente 20;
  6) una carga corta con el generador.

Solo las pruebas unitarias, en cualquier máquina (el código se copia dentro
del contenedor para no escribir en la carpeta NFS):

  docker run --rm -v "$PWD:/src:ro" maven:3.9-eclipse-temurin-21 sh -c "cp -r /src /app && cd /app && mvn test"


--------------------------------------------------------------------------------
 10. DETENER
--------------------------------------------------------------------------------

En orden inverso al despliegue (1, 2, 3):

  En la máquina 1:
    docker compose -f deploy/maquina1-consumidores.yml down
  En la máquina 2:
    docker compose -f deploy/maquina2-negocio.yml down
  En la máquina 3 (los datos se conservan):
    docker compose -f deploy/maquina3-almacenamiento.yml down
  En la máquina 3, borrando también la base de datos:
    docker compose -f deploy/maquina3-almacenamiento.yml down -v

Si cambia una IP en deploy/.env, se vuelve a ejecutar el up de la máquina que
la usa: la máquina 1 si cambió IP_NEGOCIO y la máquina 2 si cambió
IP_ALMACENAMIENTO.


--------------------------------------------------------------------------------
 11. PROBLEMAS FRECUENTES
--------------------------------------------------------------------------------

  SÍNTOMA:     Falta IP_NEGOCIO en deploy/.env (o IP_ALMACENAMIENTO)
  CAUSA:       El archivo no tiene esa línea o está mal escrita.
  CORRECCIÓN:  Se completa la línea en deploy/.env (sección 4.1).

  SÍNTOMA:     compose file "/var/lib/snapd/void/..." is invalid:
               ... no such file or directory
  CAUSA:       Docker está instalado con snap, que no puede leer carpetas
               fuera de /home (como el montaje NFS).
  CORRECCIÓN:  Se usa Docker del repositorio oficial en lugar del de snap
               (snap list docker muestra si viene de snap).

  SÍNTOMA:     permission denied while trying to connect to the Docker daemon
  CAUSA:       El usuario no está en el grupo docker.
  CORRECCIÓN:  El comando se ejecuta con sudo.

  SÍNTOMA:     La página web no abre en el navegador.
  CAUSA:       El servicio web no está arriba o el puerto 8090 está cerrado.
  CORRECCIÓN:  Se revisan el estado del servicio (sección 5.4) y el puerto
               (sección 4.3) en la máquina 1.

  SÍNTOMA:     La página abre, pero las consultas dan
               Sin respuesta de tcp://...:5555
  CAUSA:       La máquina 2 no está arriba, IP_NEGOCIO está mal o el puerto
               5555 está cerrado.
  CORRECCIÓN:  Se revisan la máquina 2, deploy/.env y el puerto, y luego se
               repite el up de la máquina 1.

  SÍNTOMA:     Las consultas dan Sin respuesta de tcp://...:5557
  CAUSA:       La máquina 3 no está arriba, IP_ALMACENAMIENTO está mal o el
               puerto 5557 está cerrado.
  CORRECCIÓN:  Se revisan la máquina 3, deploy/.env y el puerto, y luego se
               repite el up de la máquina 2.

  SÍNTOMA:     port is already allocated al levantar
  CAUSA:       Otro programa usa ese puerto en la máquina.
  CORRECCIÓN:  Se detiene el programa que ocupa el puerto.

  SÍNTOMA:     El generador de carga no puede escribir resultados/metricas.csv
  CAUSA:       Falta el permiso de la carpeta.
  CORRECCIÓN:  Se da el permiso de la sección 4.2.

  SÍNTOMA:     El protocolo da No hay conexión con PostgreSQL
  CAUSA:       El puerto 5432 de la máquina 3 está cerrado.
  CORRECCIÓN:  Se abre el puerto como en la sección 4.3.


--------------------------------------------------------------------------------
 12. ESTRUCTURA
--------------------------------------------------------------------------------

  src/main/java/srb/        código Java (un paquete por servicio)
  src/main/resources/web/   página web (HTML, CSS y JavaScript)
  src/test/java/srb/        pruebas JUnit
  db/init.sql               tablas y datos iniciales de PostgreSQL
  casos/                    casos de prueba en CSV
  deploy/                   Docker Compose de cada máquina y .env con las IP
  scripts/                  protocolo de pruebas
  docs/informe/             informe de la primera entrega (PDF)
