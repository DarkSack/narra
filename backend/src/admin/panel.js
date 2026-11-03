// Los títulos, motivos y denuncias los escriben usuarios: solo textContent, nunca innerHTML.
const $ = (tag, props = {}, ...children) => { const node = Object.assign(document.createElement(tag), props); node.append(...children); return node; };
const status = document.getElementById('status');
const when = iso => iso ? new Date(iso).toLocaleString() : '';
// Con origin y no relativa: si la página se abrió como https://usuario:clave@…, fetch rechaza la URL relativa.
const api = (path, init) => fetch(location.origin + path, init).catch(() => null);

async function load() {
  status.textContent = 'Cargando…';
  const response = await api('/admin/queue');
  if (!response?.ok) { status.textContent = `No se pudo cargar la lista (${response?.status ?? 'sin conexión'}).`; return; }
  const { pending, reported } = await response.json();
  render('pending', pending, true);
  render('reported', reported, false);
  status.textContent = `Actualizado: ${new Date().toLocaleTimeString()}`;
}

function render(id, books, actionable) {
  const section = document.getElementById(id);
  section.querySelector('.count').textContent = `(${books.length})`;
  section.querySelector('.list').replaceChildren(...(books.length ? books.map(book => card(book, actionable)) : [$('p', { className: 'empty', textContent: 'Nada por aquí.' })]));
}

function card(book, actionable) {
  const meta = [book.author, book.pages && `${book.pages} páginas`, book.pdf_bytes && `${(book.pdf_bytes / 1048576).toFixed(1)} MB`, when(book.created_at)].filter(Boolean).join(' · ');
  const node = $('article', {}, $('h3', { textContent: book.title }), $('p', { className: 'meta', textContent: meta }));
  if (book.description) node.append($('p', { textContent: book.description }));
  if (book.reason) node.append($('p', { className: 'reason', textContent: book.reason }));
  if (book.reports.length) node.append(
    $('p', { className: 'meta', textContent: `Denuncias: ${book.reports.length} (${book.reports.filter(report => report.trusted).length} de cuentas con antigüedad)` }),
    $('ul', {}, ...book.reports.map(report => $('li', { textContent: `${report.reason}${report.trusted ? '' : ' — cuenta nueva'}` }))));
  const open = $('a', { href: `/admin/books/${book.id}/pdf`, target: '_blank', rel: 'noopener', textContent: 'Abrir PDF' });
  if (!actionable) { node.append($('div', { className: 'actions' }, open)); return node; }
  const checked = $('input', { type: 'checkbox' });
  const approve = $('button', { type: 'button', className: 'primary', textContent: 'Aprobar', disabled: true });
  const reject = $('button', { type: 'button', className: 'danger', textContent: 'Rechazar' });
  checked.onchange = () => { approve.disabled = !checked.checked; };
  approve.onclick = () => act(book, 'approve', [approve, reject], checked);
  reject.onclick = () => confirm(`¿Rechazar «${book.title}»? Se borra la copia revisada.`) && act(book, 'reject', [approve, reject], checked);
  node.append($('label', {}, checked, 'Revisé todas las páginas y el texto'), $('div', { className: 'actions' }, open, approve, reject));
  return node;
}

async function act(book, action, buttons, checked) {
  for (const button of buttons) button.disabled = true;
  status.textContent = action === 'approve' ? `Aprobando «${book.title}» (vuelve a pasar el antivirus)…` : `Rechazando «${book.title}»…`;
  const response = await api(`/admin/books/${book.id}/${action}`, { method: 'POST', headers: { 'X-Narra-Admin': '1' } });
  if (response?.ok) { await load(); status.textContent = `«${book.title}» ${action === 'approve' ? 'publicado' : 'rechazado'}.`; return; }
  const error = await response?.json().catch(() => null);
  status.textContent = error?.message ? `${error.message}${error.reference ? ` (ref. ${error.reference})` : ''}` : `No se pudo completar (${response?.status ?? 'sin conexión'}).`;
  buttons[1].disabled = false; buttons[0].disabled = !checked.checked;
}

document.getElementById('refresh').onclick = load;
load();
