// Página del SRB. Habla solo con el servidor web (ServidorWeb.java), que reenvía todo al Gestor de Transacciones.

let eventos = [];        // última consulta de eventos
let estadoAntes = null;  // copia guardada con el botón «antes»
let ocurrenciaElegida = null;

const $ = id => document.getElementById(id);

// Crea un elemento HTML con clase y texto (textContent evita inyectar HTML)
function crear(etiqueta, clase = '', texto = '') {
  const elemento = document.createElement(etiqueta);
  if (clase) elemento.className = clase;
  if (texto !== '') elemento.textContent = texto;
  return elemento;
}

async function api(ruta, opciones) {
  const respuesta = await fetch(ruta, opciones);
  return respuesta.json();
}

function aviso(texto, error = false) {
  const caja = crear('div', 'aviso' + (error ? ' error' : ''), texto);
  $('avisos').append(caja);
  setTimeout(() => caja.remove(), error ? 7000 : 4000);
}

function fechaLarga(fecha) {
  return new Date(fecha + 'T00:00:00Z').toLocaleDateString('es-CO',
    { weekday: 'long', day: 'numeric', month: 'long', timeZone: 'UTC' });
}

function cliente() {
  return $('cliente').value.trim();
}

// ---------- Navegación entre secciones ----------
function mostrarVista() {
  const vista = location.hash.slice(1) || 'eventos';
  document.querySelectorAll('.vista').forEach(seccion => { seccion.hidden = seccion.id !== 'vista-' + vista; });
  document.querySelectorAll('nav a').forEach(enlace => enlace.classList.toggle('activo', enlace.dataset.vista === vista));
  if (vista === 'estado') dibujarEstado();
  if (vista === 'mis-reservas' || vista === 'operaciones') cargarOperaciones();
}

// ---------- Consulta de eventos ----------
async function consultarEventos() {
  const mes = $('mes').value;
  $('consultar').disabled = true;
  try {
    const respuesta = await api('/api/eventos?mes=' + encodeURIComponent(mes));
    if (!respuesta.ok) throw new Error(respuesta.error);
    eventos = respuesta.datos;
    dibujarEventos();
    dibujarEstado();
  } catch (error) {
    $('lista-eventos').replaceChildren(crear('p', 'error-texto', 'No se pudo consultar: ' + error.message));
  } finally {
    $('consultar').disabled = false;
    cargarOperaciones();
  }
}

function dibujarEventos() {
  const texto = $('buscar').value.trim().toLowerCase();
  const filtro = $('filtro').value;
  const lista = $('lista-eventos');
  lista.replaceChildren();
  let mostrados = 0;

  for (const evento of eventos) {
    const coincide = !texto || evento.nombre.toLowerCase().includes(texto) || evento.id.toLowerCase() === texto;
    const ocurrencias = evento.ocurrencias.filter(o =>
      filtro === 'todos' || (filtro === 'libres' ? o.libres > 0 : o.libres === 0));
    if (!coincide || ocurrencias.length === 0) continue;
    mostrados++;

    const tarjeta = crear('article', 'tarjeta');
    const cabecera = crear('div', 'tarjeta-cabecera');
    cabecera.append(crear('h3', '', evento.nombre), crear('span', 'codigo', evento.id));
    tarjeta.append(cabecera);

    for (const ocurrencia of ocurrencias) {
      const fila = crear('div', 'ocurrencia');
      const datos = crear('div', 'ocurrencia-datos');
      datos.append(crear('strong', '', fechaLarga(ocurrencia.fecha)),
                   crear('small', 'suave', 'Ocurrencia #' + ocurrencia.id));
      fila.append(datos, barra(ocurrencia));
      const boton = crear('button', '', ocurrencia.libres > 0 ? 'Reservar' : 'Agotado');
      boton.disabled = ocurrencia.libres === 0;
      boton.addEventListener('click', () => abrirReserva(evento, ocurrencia));
      fila.append(boton);
      tarjeta.append(fila);
    }
    lista.append(tarjeta);
  }
  $('conteo').textContent = mostrados + ' de ' + eventos.length + ' eventos';
  if (mostrados === 0) lista.append(crear('p', 'suave', 'No hay eventos que coincidan.'));
}

// Barra de ocupación: libres y ocupados de una ocurrencia
function barra(ocurrencia) {
  const caja = crear('div', 'ocupacion');
  caja.append(crear('span', '', ocurrencia.libres + ' libres · ' + ocurrencia.ocupados + ' ocupados'));
  const fondo = crear('div', 'barra');
  const relleno = crear('div', 'barra-relleno');
  const porcentaje = Math.round(ocurrencia.ocupados * 100 / ocurrencia.capacidad);
  relleno.style.width = porcentaje + '%';
  if (porcentaje >= 90) relleno.classList.add('casi-lleno');
  fondo.append(relleno);
  caja.append(fondo);
  return caja;
}

// ---------- Reserva ----------
function abrirReserva(evento, ocurrencia) {
  ocurrenciaElegida = ocurrencia;
  $('reserva-detalle').textContent = evento.nombre + ' (' + evento.id + ') · ' + fechaLarga(ocurrencia.fecha)
    + ' · ocurrencia #' + ocurrencia.id + ' · ' + ocurrencia.libres + ' asientos libres';
  $('reserva-cliente').value = cliente();
  $('reserva-asientos').value = 1;
  $('reserva-asientos').max = ocurrencia.libres;
  $('dialogo-reserva').showModal();
}

async function reservar() {
  const datos = {
    cliente: $('reserva-cliente').value.trim(),
    ocurrencia: ocurrenciaElegida.id,
    asientos: Number($('reserva-asientos').value)
  };
  $('cliente').value = datos.cliente;
  try {
    const respuesta = await api('/api/reservas', {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(datos)
    });
    if (respuesta.ok) {
      aviso('Reserva #' + respuesta.datos.reserva + ' confirmada: ' + datos.asientos + ' asiento(s).');
    } else {
      aviso('Reserva rechazada: ' + respuesta.error, true);
    }
  } catch (error) {
    aviso('No se pudo reservar: ' + error.message, true);
  }
  consultarEventos();
}

// ---------- Registro de operaciones y mis reservas ----------
async function cargarOperaciones() {
  let operaciones;
  try {
    operaciones = await api('/api/operaciones');
  } catch (error) {
    return;
  }
  // Registro completo
  const tabla = tablaCon(['Hora', 'Operación', 'Cliente', 'Detalle', 'Resultado', 'Tiempo']);
  for (const op of operaciones) {
    const fila = crear('tr');
    [op.hora, op.operacion, op.cliente, op.detalle].forEach(valor => fila.append(crear('td', '', valor)));
    fila.append(crear('td', op.ok ? 'bien' : 'mal', op.resultado), crear('td', 'numero', op.ms + ' ms'));
    tabla.tBodies[0].append(fila);
  }
  $('tabla-operaciones').replaceChildren(operaciones.length ? tabla : crear('p', 'suave', 'Todavía no hay operaciones.'));

  // Mis reservas: reservas confirmadas del cliente indicado
  const mias = operaciones.filter(op => op.reserva && op.cliente === cliente());
  const tablaReservas = tablaCon(['Reserva', 'Ocurrencia', 'Asientos', 'Hora']);
  for (const op of mias) {
    const fila = crear('tr');
    ['#' + op.reserva.reserva, '#' + op.reserva.ocurrencia, op.reserva.asientos, op.hora]
      .forEach(valor => fila.append(crear('td', '', valor)));
    tablaReservas.tBodies[0].append(fila);
  }
  $('lista-reservas').replaceChildren(mias.length ? tablaReservas
    : crear('p', 'suave', 'El cliente «' + cliente() + '» no tiene reservas hechas desde esta página.'));
}

function tablaCon(columnas) {
  const tabla = crear('table');
  const fila = crear('tr');
  columnas.forEach(columna => fila.append(crear('th', '', columna)));
  tabla.append(crear('thead'), crear('tbody'));
  tabla.tHead.append(fila);
  return tabla;
}

// ---------- Estado de la base de datos (antes y después) ----------
function totales(lista) {
  const todas = lista.flatMap(evento => evento.ocurrencias);
  return {
    eventos: lista.length,
    ocurrencias: todas.length,
    ocupados: todas.reduce((suma, o) => suma + o.ocupados, 0),
    libres: todas.reduce((suma, o) => suma + o.libres, 0)
  };
}

function dibujarEstado() {
  const ahora = totales(eventos);
  const antes = estadoAntes ? totales(estadoAntes) : null;
  const nombres = { eventos: 'Eventos', ocurrencias: 'Ocurrencias', ocupados: 'Asientos ocupados', libres: 'Asientos libres' };
  const caja = $('totales');
  caja.replaceChildren();
  for (const clave in nombres) {
    const total = crear('div', 'total');
    total.append(crear('strong', '', ahora[clave]), crear('span', '', nombres[clave]));
    if (antes) total.append(crear('small', 'suave', 'antes: ' + antes[clave]));
    caja.append(total);
  }

  const ocupadosAntes = {};
  if (estadoAntes) estadoAntes.flatMap(e => e.ocurrencias).forEach(o => { ocupadosAntes[o.id] = o.ocupados; });
  const tabla = tablaCon(['Ocurrencia', 'Evento', 'Fecha', 'Capacidad', 'Ocupados antes', 'Ocupados ahora', 'Libres']);
  for (const evento of eventos) {
    for (const o of evento.ocurrencias) {
      const fila = crear('tr');
      const antesValor = estadoAntes ? (ocupadosAntes[o.id] ?? '—') : '—';
      ['#' + o.id, evento.id + ' · ' + evento.nombre, o.fecha, o.capacidad, antesValor, o.ocupados, o.libres]
        .forEach(valor => fila.append(crear('td', '', valor)));
      if (estadoAntes && antesValor !== o.ocupados) fila.classList.add('cambio');
      tabla.tBodies[0].append(fila);
    }
  }
  $('tabla-estado').replaceChildren(tabla);
}

// ---------- Inicio ----------
async function iniciar() {
  window.addEventListener('hashchange', mostrarVista);
  $('consultar').addEventListener('click', consultarEventos);
  $('mes').addEventListener('change', consultarEventos);
  $('buscar').addEventListener('input', dibujarEventos);
  $('filtro').addEventListener('change', dibujarEventos);
  $('cliente').addEventListener('change', cargarOperaciones);
  $('actualizar-estado').addEventListener('click', consultarEventos);
  $('guardar-antes').addEventListener('click', () => {
    estadoAntes = structuredClone(eventos);
    dibujarEstado();
    aviso('Estado «antes» guardado. Con «Actualizar» se compara con el estado actual.');
  });
  $('form-reserva').addEventListener('submit', evento => {
    if (evento.submitter && evento.submitter.value === 'confirmar') reservar();
  });

  const config = await api('/api/config');
  $('mes').value = config.mes;
  mostrarVista();
  consultarEventos();
  setInterval(cargarOperaciones, 5000);
}

iniciar();
