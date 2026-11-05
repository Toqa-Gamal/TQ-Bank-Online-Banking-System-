const API = '/api';
const $ = id => document.getElementById(id);
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
let token = sessionStorage.getItem('token');
let me = JSON.parse(sessionStorage.getItem('me') || 'null');
let accounts = [];
let histPage = 1, histTotalPages = 1;
let modalCtx = null;

function money(n) {
  return Math.abs(Number(n)).toLocaleString('en-US', {minimumFractionDigits: 2, maximumFractionDigits: 2}) + ' EGP';
}
function setMsg(id, text, ok) {
  const el = $(id); el.textContent = text || ''; el.className = text ? (ok ? 'msg ok' : 'msg err') : '';
}
function num(v) { const x = parseFloat(String(v).trim()); return isNaN(x) ? null : x; }

async function api(method, path, body) {
  const headers = {'Content-Type': 'application/json'};
  if (token) headers['Authorization'] = 'Bearer ' + token;
  const r = await fetch(API + path, {method, headers, body: body !== undefined ? JSON.stringify(body) : undefined});
  const ct = r.headers.get('content-type') || '';
  const data = ct.includes('json') ? await r.json() : await r.text();
  if (!r.ok) {
    if (r.status === 401 && !path.startsWith('/auth')) {
      token = null; me = null; sessionStorage.clear();
      showAuth('Your session has expired. Please log in again.');
    }
    throw new Error((data && data.error) || 'Request failed');
  }
  return data;
}

/* ---------- navigation ---------- */
function showAuth(message) {
  $('nav').classList.add('hidden');
  document.querySelectorAll('.app-view').forEach(v => v.classList.add('hidden'));
  $('view-auth').classList.remove('hidden');
  setMsg('login-error', message || '', false);
}
function showView(name) {
  $('view-auth').classList.add('hidden');
  document.querySelectorAll('.app-view').forEach(v => v.classList.add('hidden'));
  $('view-' + name).classList.remove('hidden');
  document.querySelectorAll('#nav button[data-view]').forEach(b => b.classList.toggle('active', b.dataset.view === name));
  if (name === 'accounts' || name === 'open') loadAccounts();
  if (name === 'transact' || name === 'history') loadAccounts().then(() => { if (name === 'history') loadHistory(1); });
  if (name === 'admin') loadAdmin();
}
function showApp() {
  $('nav').classList.remove('hidden');
  $('welcome').textContent = 'Welcome, ' + me.fullName;
  $('hero-title').textContent = 'Hello, ' + me.fullName;
  $('hero-sub').textContent = me.role === 'ADMIN' ? 'You are signed in as admin. Manage accounts from the Admin tab.' : 'Here is a summary of your accounts.';
  $('nav-admin').classList.toggle('hidden', me.role !== 'ADMIN');
  showView('accounts');
}
document.querySelectorAll('#nav button[data-view]').forEach(b => b.addEventListener('click', () => showView(b.dataset.view)));

$('logout-btn').addEventListener('click', async () => {
  try { await api('POST', '/auth/logout'); } catch (e) {}
  token = null; me = null;
  showAuth('');
});

/* ---------- auth ---------- */
$('tab-login').addEventListener('click', () => {
  $('login-form').classList.remove('hidden'); $('register-form').classList.add('hidden');
  $('tab-login').classList.add('active'); $('tab-register').classList.remove('active');
});
$('tab-register').addEventListener('click', () => {
  $('register-form').classList.remove('hidden'); $('login-form').classList.add('hidden');
  $('tab-register').classList.add('active'); $('tab-login').classList.remove('active');
});

$('login-form').addEventListener('submit', async e => {
  e.preventDefault();
  setMsg('login-error', '');
  const identifier = $('login-identifier').value.trim(), password = $('login-password').value;
  if (!identifier || !password) { setMsg('login-error', 'Email/phone and password are required.', false); return; }
  try {
    const d = await api('POST', '/auth/login', {identifier, password});
    token = d.token; me = {userId: d.userId, fullName: d.fullName, role: d.role};
    sessionStorage.setItem('token', token); sessionStorage.setItem('me', JSON.stringify(me));
    $('login-password').value = '';
    showApp();
  } catch (err) { setMsg('login-error', err.message, false); }
});

function validateReg() {
  ['fullname', 'email', 'phone', 'dob', 'password', 'confirm'].forEach(f => $('err-reg-' + f).textContent = '');
  let ok = true;
  const bad = (f, m) => { $('err-reg-' + f).textContent = m; ok = false; };
  if (!/^[A-Za-z ]{3,50}$/.test($('reg-fullname').value.trim())) bad('fullname', 'Full name must be 3-50 alphabetic characters.');
  if (!/^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/.test($('reg-email').value.trim())) bad('email', 'Invalid email format.');
  if (!/^01[0-5]\d{8}$/.test($('reg-phone').value.trim())) bad('phone', 'Phone must be 11 digits starting with 010, 011, 012 or 015.');
  const dob = $('reg-dob').value;
  if (!dob) bad('dob', 'Date of birth is required.');
  else {
    const d = new Date(dob), t = new Date();
    let age = t.getFullYear() - d.getFullYear();
    const m = t.getMonth() - d.getMonth();
    if (m < 0 || (m === 0 && t.getDate() < d.getDate())) age--;
    if (d >= t) bad('dob', 'Date of birth must be in the past.');
    else if (age < 18) bad('dob', 'You must be at least 18 years old.');
  }
  if (!/^(?=.*[A-Z])(?=.*[a-z])(?=.*\d)(?=.*[!@#$%^&*]).{8,20}$/.test($('reg-password').value))
    bad('password', 'Password must be 8-20 chars with uppercase, lowercase, digit and special character (!@#$%^&*).');
  return ok;
}
$('register-form').addEventListener('submit', async e => {
  e.preventDefault();
  setMsg('reg-error', ''); setMsg('reg-success', '');
  if (!validateReg()) return;
  try {
    await api('POST', '/auth/register', {
      fullName: $('reg-fullname').value.trim(), email: $('reg-email').value.trim(), phone: $('reg-phone').value.trim(),
      dateOfBirth: $('reg-dob').value, password: $('reg-password').value, confirmPassword: $('reg-confirm').value
    });
    setMsg('reg-success', 'Registration successful. You can log in now.', true);
    $('register-form').reset();
  } catch (err) { setMsg('reg-error', err.message, false); }
});

/* ---------- accounts ---------- */
async function loadAccounts() {
  const d = await api('GET', '/accounts');
  accounts = d.accounts;
  $('accounts-body').innerHTML = accounts.map(a => {
    const no = esc(a.accountNumber);
    return `<tr id="row-${no}" qa-test="account-row-${no}" data-account-id="${a.id}">` +
      `<td qa-test="account-number-${no}">${no}</td>` +
      `<td qa-test="account-type-${no}">${esc(a.accountType)}</td>` +
      `<td class="bal" qa-test="account-balance-${no}">${money(a.balance)}</td>` +
      `<td qa-test="account-status-${no}"><span class="badge ${esc(a.status)}">${esc(a.status)}</span></td></tr>`;
  }).join('') ||
    '<tr><td colspan="4" id="no-accounts"><div class="empty"><div qa-test="accounts-empty">You have no accounts yet. Open your first account.</div><button class="btn sm" type="button" data-go="open" qa-test="accounts-empty-open">Open account</button></div></td></tr>';
  const total = accounts.filter(a => a.status !== 'FROZEN').reduce((s, a) => s + Number(a.balance), 0);
  $('total-balance').textContent = money(total);
  $('accounts-count').textContent = accounts.length;
  const opts = accounts.map(a => `<option value="${a.id}">${esc(a.accountNumber)} (${esc(a.accountType)})</option>`).join('');
  ['tx-account', 'tr-from', 'hist-account'].forEach(id => { const cur = $(id).value; $(id).innerHTML = opts; if (cur) $(id).value = cur; });
}

$('open-btn').addEventListener('click', async () => {
  setMsg('open-msg', '');
  try {
    const d = await api('POST', '/accounts', {accountType: $('open-type').value, openingBalance: num($('open-balance').value)});
    setMsg('open-msg', `Account ${d.accountNumber} opened. Reference: ${d.referenceId}`, true);
    $('open-balance').value = '';
    loadAccounts();
  } catch (err) { setMsg('open-msg', err.message, false); }
});

/* ---------- transactions ---------- */
function showReceipt(r) {
  const el = $('receipt');
  el.classList.remove('hidden');
  el.innerHTML = `<div>Reference: <strong id="receipt-ref" qa-test="receipt-reference">${esc(r.referenceId)}</strong></div>` +
    `<div>Type: <span id="receipt-type" qa-test="receipt-type">${esc(r.type)}</span></div>` +
    `<div>Account: <span id="receipt-account" qa-test="receipt-account">${esc(r.accountNumber)}</span></div>` +
    `<div>Amount: <span id="receipt-amount" qa-test="receipt-amount">${money(r.amount)}</span></div>` +
    `<div>Balance after: <span id="receipt-balance" qa-test="receipt-balance-after">${Number(r.balanceAfter).toLocaleString('en-US', {minimumFractionDigits: 2})} EGP</span></div>` +
    `<div>Date/Time: <span id="receipt-date" qa-test="receipt-datetime">${esc(r.dateTime)}</span></div>`;
}
async function doTx(kind) {
  setMsg('tx-msg', ''); $('receipt').classList.add('hidden');
  const id = $('tx-account').value;
  const acc = accounts.find(a => String(a.id) === String(id));
  const oldBal = acc ? acc.balance : 0;
  try {
    const r = await api('POST', `/accounts/${id}/${kind}`, {amount: num($('tx-amount').value)});
    setMsg('tx-msg', `${kind === 'deposit' ? 'Deposit' : 'Withdrawal'} successful. New balance: ${money(oldBal)}`, true);
    showReceipt(r);
    $('tx-amount').value = '';
    loadAccounts();
  } catch (err) { setMsg('tx-msg', err.message, false); }
}
$('deposit-btn').addEventListener('click', () => doTx('deposit'));
$('withdraw-btn').addEventListener('click', () => doTx('withdraw'));

$('transfer-btn').addEventListener('click', async () => {
  setMsg('tr-msg', '');
  try {
    const r = await api('POST', '/transfers', {
      fromAccountId: Number($('tr-from').value) || null, toAccountNumber: $('tr-to').value.trim(),
      amount: num($('tr-amount').value), note: $('tr-note').value || null
    });
    setMsg('tr-msg', `Transfer successful. Reference: ${r.referenceId}. Balance after: ${Number(r.balanceAfter).toLocaleString('en-US', {minimumFractionDigits: 2})} EGP`, true);
    $('tr-to').value = ''; $('tr-amount').value = ''; $('tr-note').value = '';
    loadAccounts();
  } catch (err) { setMsg('tr-msg', err.message, false); }
});

/* ---------- history ---------- */
function histQuery(withPage) {
  const p = new URLSearchParams();
  if ($('hist-type').value) p.set('type', $('hist-type').value);
  if ($('hist-from').value) p.set('from', $('hist-from').value);
  if ($('hist-to').value) p.set('to', $('hist-to').value);
  if (withPage) p.set('page', histPage);
  return p.toString();
}
async function loadHistory(page) {
  histPage = page; setMsg('hist-msg', '');
  const id = $('hist-account').value;
  if (!id) { $('hist-body').innerHTML = ''; setMsg('hist-msg', 'No transactions found.', false); return; }
  try {
    const d = await api('GET', `/accounts/${id}/transactions?${histQuery(true)}`);
    histTotalPages = Math.max(d.totalPages, 1);
    $('hist-body').innerHTML = d.transactions.map(t =>
      `<tr qa-test="history-row"><td qa-test="history-reference">${esc(t.referenceId)}</td><td qa-test="history-type-cell">${esc(t.type)}</td>` +
      `<td qa-test="history-amount">${money(t.amount)}</td><td qa-test="history-datetime">${esc(t.createdAt)}</td>` +
      `<td qa-test="history-balance-after">${Number(t.balanceAfter).toLocaleString('en-US', {minimumFractionDigits: 2})}</td></tr>`).join('');
    $('hist-page').textContent = `Page ${d.page} of ${histTotalPages}`;
    $('hist-prev').disabled = histPage <= 1;
    if (d.message) setMsg('hist-msg', d.message, false);
  } catch (err) { $('hist-body').innerHTML = ''; setMsg('hist-msg', err.message, false); }
}
$('hist-filter-btn').addEventListener('click', () => loadHistory(1));
$('hist-account').addEventListener('change', () => loadHistory(1));
$('hist-prev').addEventListener('click', () => { if (histPage > 1) loadHistory(histPage - 1); });
$('hist-next').addEventListener('click', () => loadHistory(histPage + 1));
$('export-btn').addEventListener('click', async () => {
  const id = $('hist-account').value;
  try {
    const csv = await api('GET', `/accounts/${id}/transactions/export?${histQuery(false)}`);
    const a = document.createElement('a');
    a.href = URL.createObjectURL(new Blob([csv], {type: 'text/csv'}));
    a.download = 'transactions.csv'; a.click();
    setMsg('hist-msg', 'CSV exported.', true);
  } catch (err) { setMsg('hist-msg', err.message, false); }
});

/* ---------- admin ---------- */
async function loadAdmin() {
  setMsg('admin-msg', '');
  try {
    const rows = await api('GET', '/admin/accounts?search=' + encodeURIComponent($('admin-search').value.trim()));
    $('admin-body').innerHTML = rows.map(a => {
      const no = esc(a.accountNumber);
      const frozen = a.status === 'FROZEN';
      const action = frozen ? 'unfreeze' : 'freeze';
      return `<tr id="admin-row-${no}" qa-test="admin-row-${no}"><td qa-test="admin-account-${no}">${no}</td><td qa-test="admin-owner-${no}">${esc(a.owner)}</td><td>${esc(a.accountType)}</td>` +
        `<td qa-test="admin-balance-${no}">${money(a.balance)}</td><td qa-test="admin-status-${no}"><span class="badge ${esc(a.status)}">${esc(a.status)}</span></td>` +
        `<td><button id="${action}-btn-${a.id}" qa-test="admin-${action}-${no}" class="btn sm ${frozen ? 'ok' : 'danger'}" data-id="${a.id}" data-action="${action}">${frozen ? 'Unfreeze' : 'Freeze'}</button></td></tr>`;
    }).join('');
    document.querySelectorAll('#admin-body button').forEach(b => b.addEventListener('click', () => openModal(b.dataset.id, b.dataset.action)));
  } catch (err) { setMsg('admin-msg', err.message, false); }
}
$('admin-search-btn').addEventListener('click', loadAdmin);
function openModal(id, action) {
  modalCtx = {id, action};
  $('modal-title').textContent = (action === 'freeze' ? 'Freeze' : 'Unfreeze') + ' account';
  $('modal-reason').value = ''; $('modal-error').textContent = '';
  $('modal').classList.remove('hidden');
}
$('modal-cancel').addEventListener('click', () => $('modal').classList.add('hidden'));
$('modal-confirm').addEventListener('click', async () => {
  try {
    const d = await api('PUT', `/admin/accounts/${modalCtx.id}/${modalCtx.action}`, {reason: $('modal-reason').value});
    $('modal').classList.add('hidden');
    setMsg('admin-msg', d.message, true);
    loadAdmin();
  } catch (err) { $('modal-error').textContent = err.message; }
});

document.addEventListener('click', e => { const b = e.target.closest('[data-go]'); if (b) showView(b.dataset.go); });

/* ---------- boot ---------- */
if (token && me) showApp(); else showAuth('');
