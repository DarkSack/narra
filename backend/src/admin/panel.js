// Los títulos, motivos y denuncias los escriben usuarios: solo textContent, nunca innerHTML.
const $ = (tag, props = {}, ...children) => { const node = Object.assign(document.createElement(tag), props); node.append(...children.filter(Boolean)); return node; };
const icon = name => {
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  const use = document.createElementNS('http://www.w3.org/2000/svg', 'use');
  use.setAttribute('href', `#i-${name}`); svg.setAttribute('class', 'i'); svg.setAttribute('aria-hidden', 'true'); svg.append(use);
  return svg;
};
// Con origin y no relativa: si la página se abrió como https://usuario:clave@…, fetch rechaza la URL relativa.
const api = (path, init = {}) => fetch(location.origin + path, { ...init, headers: { 'X-Narra-Admin': '1', ...init.headers } }).catch(() => null);
const ago = new Intl.RelativeTimeFormat('es', { numeric: 'auto' });
function when(iso) {
  const minutes = (Date.parse(iso) - Date.now()) / 60_000;
  if (!Number.isFinite(minutes)) return '';
  for (const [unit, size] of [['day', 1440], ['hour', 60], ['minute', 1]]) if (Math.abs(minutes) >= size || unit === 'minute') return ago.format(Math.round(minutes / size), unit);
}
const CLOTH = ['#7A2E2A', '#2F4F3E', '#2C3E63', '#7A5A1E', '#4E3360', '#3F5560'];
const cloth = text => CLOTH[[...text].reduce((sum, char) => (sum * 31 + char.codePointAt(0)) >>> 0, 7) % CLOTH.length];

const login = document.getElementById('login'), app = document.getElementById('app');
const password = document.getElementById('password'), loginError = document.getElementById('login-error');
const toast = document.getElementById('toast');
let toastTimer;
function notify(text, error = false) {
  toast.replaceChildren(icon(error ? 'x' : 'check'), text);
  toast.className = `toast${error ? ' error' : ''}`; toast.hidden = false;
  clearTimeout(toastTimer); toastTimer = setTimeout(() => { toast.hidden = true; }, 6000);
}
const failure = async (response, fallback) => (await response?.json().catch(() => null))?.message ?? `${fallback} (${response?.status ?? 'sin conexión'})`;

function showLogin(message = '') {
  app.hidden = true; login.hidden = false;
  loginError.textContent = message;
  password.focus();
}

async function load() {
  const refresh = document.getElementById('refresh');
  refresh.setAttribute('aria-busy', 'true');
  const response = await api('/admin/queue');
  refresh.removeAttribute('aria-busy');
  if (response?.status === 401) return showLogin(app.hidden ? '' : 'Tu sesión terminó. Vuelve a entrar.');
  if (!response?.ok) { app.hidden = false; return notify(await failure(response, 'No se pudo cargar la lista'), true); }
  const { pending, reported } = await response.json();
  login.hidden = true; app.hidden = false;
  document.getElementById('stat-pending').textContent = pending.length;
  document.getElementById('stat-reported').textContent = reported.length;
  render('pending', pending, true, 'Nada pendiente. Todo está revisado.');
  render('reported', reported, false, 'Ningún libro publicado tiene denuncias abiertas.');
}

function render(id, books, actionable, empty) {
  document.querySelector(`#${id} .list`).replaceChildren(...(books.length
    ? books.map(book => card(book, actionable))
    : [$('div', { className: 'empty' }, icon('check'), $('span', { textContent: empty }))]));
}

function card(book, actionable) {
  const trusted = book.reports.filter(report => report.trusted).length;
  const cover = $('div', { className: 'cover' }, $('span', { className: 'initial', textContent: [...book.title.trim()][0]?.toUpperCase() ?? '?' }));
  cover.style.setProperty('--cloth', cloth(book.title));
  cover.setAttribute('aria-hidden', 'true');
  const badges = $('div', { className: 'badges' },
    actionable && !book.reports.length && $('span', { className: 'badge new', textContent: 'Nuevo' }),
    book.reports.length && $('span', { className: 'badge warn' }, icon('flag'), `${book.reports.length} ${book.reports.length === 1 ? 'denuncia' : 'denuncias'}`));
  const facts = $('ul', { className: 'facts' }, ...[
    book.pages && `${book.pages} páginas`,
    book.pdf_bytes && `${(book.pdf_bytes / 1048576).toFixed(1)} MB`,
    book.created_at && `Subido ${when(book.created_at)}`,
  ].filter(Boolean).map(text => $('li', { textContent: text })));
  const reports = book.reports.length && $('div', { className: 'reports' },
    $('h4', {}, icon('flag'), `Denuncias · ${trusted} de cuentas con antigüedad`),
    $('ul', {}, ...book.reports.map(report => $('li', {}, report.reason, !report.trusted && $('span', { className: 'chip', textContent: 'cuenta nueva' })))));
  const open = $('a', { className: 'button', href: `/admin/books/${book.id}/pdf`, target: '_blank', rel: 'noopener' }, icon('file'), 'Abrir PDF');
  const body = $('div', { className: 'book-body' }, badges,
    $('h3', { textContent: book.title }),
    book.author && $('p', { className: 'byline', textContent: book.author }),
    facts,
    book.description && $('p', { className: 'desc', textContent: book.description }),
    book.reason && $('p', { className: 'reason', textContent: book.reason }),
    reports);
  if (!actionable) body.append($('div', { className: 'review' }, $('div', { className: 'actions' }, open)));
  else {
    const checked = $('input', { type: 'checkbox' });
    const approve = $('button', { type: 'button', className: 'button primary', disabled: true }, icon('check'), 'Aprobar');
    const reject = $('button', { type: 'button', className: 'button danger' }, icon('x'), 'Rechazar');
    checked.onchange = () => { approve.disabled = !checked.checked; };
    approve.onclick = () => act(book, 'approve', approve, reject, checked);
    reject.onclick = async () => { if (await confirmReject(book)) act(book, 'reject', approve, reject, checked); };
    body.append($('div', { className: 'review' },
      $('label', { className: 'check' }, checked, 'Revisé todas las páginas y el texto'),
      $('div', { className: 'actions' }, open, $('span', { className: 'spacer' }), reject, approve)));
  }
  return $('article', { className: 'book' }, cover, body);
}

function confirmReject(book) {
  const dialog = document.getElementById('confirm');
  document.getElementById('confirm-text').textContent = `«${book.title}» no se publicará y se borrará la copia revisada. Quien lo subió verá «No aprobado en revisión humana». No se puede deshacer.`;
  dialog.returnValue = '';
  dialog.showModal();
  return new Promise(resolve => dialog.addEventListener('close', () => resolve(dialog.returnValue === 'ok'), { once: true }));
}

async function act(book, action, approve, reject, checked) {
  const button = action === 'approve' ? approve : reject;
  const label = button.lastChild.textContent;
  approve.disabled = reject.disabled = true;
  button.lastChild.textContent = action === 'approve' ? 'Aprobando…' : 'Rechazando…';
  const response = await api(`/admin/books/${book.id}/${action}`, { method: 'POST' });
  button.lastChild.textContent = label;
  if (response?.ok) { notify(`«${book.title}» ${action === 'approve' ? 'publicado' : 'rechazado'}.`); return load(); }
  if (response?.status === 401) return showLogin('Tu sesión terminó. Vuelve a entrar.');
  const error = await response?.json().catch(() => null);
  notify(error?.message ? `${error.message}${error.reference ? ` (ref. ${error.reference.slice(0, 8)})` : ''}` : `No se pudo completar (${response?.status ?? 'sin conexión'}).`, true);
  reject.disabled = false; approve.disabled = !checked.checked;
}

document.getElementById('login-form').onsubmit = async event => {
  event.preventDefault();
  if (!password.value) { loginError.textContent = 'Escribe la contraseña.'; return password.focus(); }
  const submit = document.getElementById('login-submit');
  submit.disabled = true; submit.textContent = 'Entrando…'; loginError.textContent = '';
  const response = await api('/admin/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ password: password.value }) });
  submit.disabled = false; submit.textContent = 'Entrar';
  if (!response?.ok) { loginError.textContent = await failure(response, 'No se pudo entrar'); password.select(); return; }
  password.value = '';
  load();
};
document.getElementById('reveal').onclick = event => {
  const button = event.currentTarget, shown = password.type === 'password';
  password.type = shown ? 'text' : 'password';
  button.setAttribute('aria-pressed', String(shown));
  button.setAttribute('aria-label', shown ? 'Ocultar contraseña' : 'Mostrar contraseña');
  button.replaceChildren(icon(shown ? 'eye-off' : 'eye'));
  password.focus();
};
document.getElementById('refresh').onclick = load;
document.getElementById('logout').onclick = async () => { await api('/admin/logout', { method: 'POST' }); showLogin(); };
load();
