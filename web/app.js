// Cuaderno de bolsillo — web (solo lectura, por ahora).
// Lee en tiempo real de Firestore: users/{uid}/days|entries|habits|habitChecks.
import { initializeApp } from "https://www.gstatic.com/firebasejs/13.2.0/firebase-app.js";
import {
  getAuth, onAuthStateChanged, GoogleAuthProvider, signInWithPopup, signInWithRedirect, signOut,
} from "https://www.gstatic.com/firebasejs/13.2.0/firebase-auth.js";
import {
  getFirestore, collection, doc, onSnapshot,
} from "https://www.gstatic.com/firebasejs/13.2.0/firebase-firestore.js";

// Configuración pública del proyecto (no es secreta: protegen las reglas de Firestore).
const firebaseConfig = {
  apiKey: "AIzaSyAYxroF94x_Y8kM08YXlD0OctP3ZXfQUqg",
  authDomain: "cuaderno-de-bolsillo.firebaseapp.com",
  projectId: "cuaderno-de-bolsillo",
  storageBucket: "cuaderno-de-bolsillo.firebasestorage.app",
  messagingSenderId: "1087631261659",
  appId: "1:1087631261659:web:4e92b907df5f33e65f31fe",
};

const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const db = getFirestore(app);

// ---------- Idioma ----------
const lang = (navigator.language || "en").toLowerCase().startsWith("es") ? "es" : "en";
const T = {
  es: {
    appName: "Cuaderno de bolsillo", loginHint: "Tu cuaderno, copiado desde el móvil. Solo tu cuenta puede verlo.",
    signIn: "Entrar con Google", signOut: "Salir", tabDay: "Día", tabWeek: "Semana", tabPending: "Pendientes",
    today: "Hoy", thisWeek: "Esta semana", week: "Semana", habits: "Hábitos", summary: "Frase del día", log: "Registro",
    days: "Días", nothing: "Nada apuntado", noHabits: "Sin hábitos",
    statusDone: "Pasado al cuaderno ✓", statusChanged: "Cambios sin pasar", statusPending: "Pendiente de pasar al cuaderno",
    pillDone: "✓ Pasado", pillChanged: "Cambios", pillPending: "Pendiente",
    pendingTitle: "Pendientes de pasar", allDone: "Todo pasado al cuaderno ✓",
    entries: (n) => `${n} ${n === 1 ? "entrada" : "entradas"}`, habitsN: (n) => `${n} ${n === 1 ? "hábito" : "hábitos"}`,
    more: (n) => `y ${n} más…`, lastSync: (s) => `Última copia: ${s}`, noSync: "Aún sin copia",
    notAllowed: "No se pudo entrar: ", prev: "Anterior", next: "Siguiente",
  },
  en: {
    appName: "Pocket Notebook", loginHint: "Your notebook, backed up from your phone. Only your account can see it.",
    signIn: "Sign in with Google", signOut: "Sign out", tabDay: "Day", tabWeek: "Week", tabPending: "To copy",
    today: "Today", thisWeek: "This week", week: "Week", habits: "Habits", summary: "Line of the day", log: "Log",
    days: "Days", nothing: "Nothing noted", noHabits: "No habits",
    statusDone: "Copied to the notebook ✓", statusChanged: "Changes not copied yet", statusPending: "Not copied to the notebook yet",
    pillDone: "✓ Copied", pillChanged: "Changes", pillPending: "Pending",
    pendingTitle: "To copy into the notebook", allDone: "Everything is in the notebook ✓",
    entries: (n) => `${n} ${n === 1 ? "entry" : "entries"}`, habitsN: (n) => `${n} ${n === 1 ? "habit" : "habits"}`,
    more: (n) => `and ${n} more…`, lastSync: (s) => `Last backup: ${s}`, noSync: "Not backed up yet",
    notAllowed: "Couldn't sign in: ", prev: "Previous", next: "Next",
  },
}[lang];
document.documentElement.lang = lang;
document.title = T.appName;
document.querySelectorAll("[data-i18n]").forEach((el) => { el.textContent = T[el.dataset.i18n]; });

// ---------- Fechas (epochDay = días desde 1970-01-01, igual que en la app) ----------
const DAY_MS = 86400000;
const todayEpoch = () => {
  const d = new Date();
  return Math.floor(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()) / DAY_MS);
};
const toDate = (epoch) => new Date(epoch * DAY_MS);
const fmt = (epoch, opts) => new Intl.DateTimeFormat(lang, { timeZone: "UTC", ...opts }).format(toDate(epoch));
const cap = (s) => s.charAt(0).toUpperCase() + s.slice(1);
const weekdayName = (e) => cap(fmt(e, { weekday: "long" }));
const longDate = (e) => fmt(e, { day: "numeric", month: "long", year: "numeric" });
const dayMonth = (e) => cap(fmt(e, { weekday: "long", day: "numeric", month: "long" }));
const startOfWeek = (e) => e - ((toDate(e).getUTCDay() + 6) % 7); // lunes
const weekLetter = (e) => fmt(e, { weekday: "narrow" }).toUpperCase();
const stamp = (ms) => new Intl.DateTimeFormat(lang, { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" }).format(new Date(ms));

// ---------- Estado ----------
const state = {
  view: "day", day: todayEpoch(), weekStart: startOfWeek(todayEpoch()),
  days: new Map(), entries: new Map(), habits: new Map(), checks: new Map(), lastSyncAt: 0,
};
let unsubscribers = [];

const $ = (sel) => document.querySelector(sel);
const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

// ---------- Datos derivados ----------
const entriesOf = (epoch) =>
  [...state.entries.values()].filter((e) => e.epochDay === epoch && !e.deleted)
    .sort((a, b) => a.position - b.position || a.id - b.id);
const activeHabits = () => [...state.habits.values()].filter((h) => !h.archived).sort((a, b) => a.position - b.position);
const doneHabitIds = (epoch) =>
  new Set([...state.checks.values()].filter((c) => c.epochDay === epoch && c.done).map((c) => c.habitId));

function dayStatus(epoch) {
  const d = state.days.get(epoch);
  const hasContent = entriesOf(epoch).length > 0 || doneHabitIds(epoch).size > 0 || (d?.summary || "").trim() !== "";
  if (d?.transcribed) return "done";
  if (d?.lastTranscribedAt) return "changed";
  return hasContent ? "pending" : null;
}
const isNew = (epoch, updatedAt) => {
  const d = state.days.get(epoch);
  return d && !d.transcribed && d.lastTranscribedAt && updatedAt > d.lastTranscribedAt;
};
function pendingDays() {
  const all = new Set([...state.days.keys(), ...[...state.entries.values()].map((e) => e.epochDay)]);
  return [...all].filter((e) => ["pending", "changed"].includes(dayStatus(e))).sort((a, b) => a - b);
}
const finished = (e) => e.type === "TASK" && e.status !== "OPEN";

// ---------- Vistas ----------
function entryList(epoch, limit = Infinity) {
  const list = entriesOf(epoch);
  if (!list.length) return `<p class="empty">${T.nothing}</p>`;
  const items = list.slice(0, limit).map((e) =>
    `<li class="entry ${finished(e) ? "finished" : ""}" ${isNew(epoch, e.updatedAt) ? 'style="background:var(--accent-soft);border-radius:10px;padding-inline:6px"' : ""}>
       <span class="sym">${esc(e.symbol)}</span><span>${esc(e.text)}</span></li>`).join("");
  const more = list.length > limit ? `<p class="empty">${T.more(list.length - limit)}</p>` : "";
  return `<ul class="entries">${items}</ul>${more}`;
}
function pill(status) {
  if (!status) return "";
  const label = { done: T.pillDone, changed: T.pillChanged, pending: T.pillPending }[status];
  return `<span class="pill ${status}">${label}</span>`;
}

function renderDay() {
  const e = state.day;
  const isToday = e === todayEpoch();
  const status = dayStatus(e);
  const d = state.days.get(e);
  const done = doneHabitIds(e);
  const habits = activeHabits();
  const statusText = { done: T.statusDone, changed: T.statusChanged, pending: T.statusPending }[status];
  return `
    <div class="nav">
      <button class="icon-btn" data-act="prev" aria-label="${T.prev}">‹</button>
      <div class="title"><h2>${isToday ? `${T.today} · ` : ""}${weekdayName(e)}</h2><div class="sub">${longDate(e)}</div></div>
      ${isToday ? "" : `<button class="btn small" data-act="today">${T.today}</button>`}
      <button class="icon-btn" data-act="next" aria-label="${T.next}">›</button>
    </div>
    ${status ? `<div class="status ${status}">${statusText}${d?.lastTranscribedAt && status === "done" ? ` · <span class="empty">${stamp(d.lastTranscribedAt)}</span>` : ""}</div>` : ""}
    <div class="section-title">${T.habits}</div>
    <div class="chips">${habits.length ? habits.map((h) => `<span class="chip ${done.has(h.id) ? "on" : ""}">${done.has(h.id) ? "✓ " : ""}${esc(h.name)}</span>`).join("") : `<span class="empty">${T.noHabits}</span>`}</div>
    ${d?.summary ? `<div class="section-title">${T.summary}</div><p class="summary">“${esc(d.summary)}”</p>` : ""}
    <div class="section-title">${T.log}</div>
    ${entryList(e)}`;
}

function renderWeek() {
  const start = state.weekStart;
  const days = [...Array(7).keys()].map((i) => start + i);
  const today = todayEpoch();
  const isCurrent = start === startOfWeek(today);
  const habits = activeHabits();
  const grid = habits.length ? `
    <div class="section-title">${T.habits}</div>
    <div class="card"><div class="grid">
      <span></span>${days.map((e) => `<span class="h ${e === today ? "today" : ""}">${weekLetter(e)}<br>${toDate(e).getUTCDate()}</span>`).join("")}
      ${habits.map((h) => `<span class="name">${esc(h.name)}</span>${days.map((e) => `<span class="dot ${doneHabitIds(e).has(h.id) ? "on" : ""}"></span>`).join("")}`).join("")}
    </div></div>` : "";
  const cards = days.map((e) => {
    const d = state.days.get(e);
    return `<div class="card clickable ${e === today ? "today" : ""}" data-open="${e}">
      <div style="display:flex;justify-content:space-between;gap:8px;align-items:center"><strong>${dayMonth(e)}</strong>${pill(dayStatus(e))}</div>
      ${d?.summary ? `<p class="summary" style="margin:4px 0">“${esc(d.summary)}”</p>` : ""}
      ${entryList(e, 6)}</div>`;
  }).join("");
  const range = `${fmt(start, { day: "numeric", month: "short" })} – ${fmt(start + 6, { day: "numeric", month: "short" })}`;
  return `
    <div class="nav">
      <button class="icon-btn" data-act="prevWeek" aria-label="${T.prev}">‹</button>
      <div class="title"><h2>${isCurrent ? T.thisWeek : T.week}</h2><div class="sub">${range}</div></div>
      ${isCurrent ? "" : `<button class="btn small" data-act="thisWeek">${T.today}</button>`}
      <button class="icon-btn" data-act="nextWeek" aria-label="${T.next}">›</button>
    </div>
    ${grid}
    <div class="section-title">${T.days}</div>${cards}`;
}

function renderPending() {
  const list = pendingDays();
  if (!list.length) return `<h2>${T.pendingTitle}</h2><p class="empty">${T.allDone}</p>`;
  return `<h2>${T.pendingTitle}</h2>` + list.map((e) => {
    const d = state.days.get(e);
    const parts = [];
    const n = entriesOf(e).length; if (n) parts.push(T.entries(n));
    const h = doneHabitIds(e).size; if (h) parts.push(T.habitsN(h));
    return `<div class="card clickable" data-open="${e}">
      <div style="display:flex;justify-content:space-between;gap:8px;align-items:center"><strong>${e === todayEpoch() ? `${T.today} · ` : ""}${dayMonth(e)}</strong>${pill(dayStatus(e))}</div>
      <div class="empty">${parts.join(" · ")}</div>
      ${d?.summary ? `<p class="summary" style="margin:4px 0 0">“${esc(d.summary)}”</p>` : ""}</div>`;
  }).join("");
}

function render() {
  document.querySelectorAll(".tab").forEach((t) => t.classList.toggle("active", t.dataset.view === state.view));
  const view = $("#view");
  view.innerHTML = { day: renderDay, week: renderWeek, pending: renderPending }[state.view]();
  view.style.animation = "none"; void view.offsetWidth; view.style.animation = ""; // repetir la animación
  const n = pendingDays().length;
  const badge = $("#pending-badge");
  badge.hidden = n === 0; badge.textContent = n;
  $("#last-sync").textContent = state.lastSyncAt ? T.lastSync(stamp(state.lastSyncAt)) : T.noSync;
}

// ---------- Eventos ----------
document.addEventListener("click", (ev) => {
  const tab = ev.target.closest(".tab");
  if (tab) { state.view = tab.dataset.view; render(); return; }
  const open = ev.target.closest("[data-open]");
  if (open) { state.day = Number(open.dataset.open); state.view = "day"; render(); return; }
  const act = ev.target.closest("[data-act]")?.dataset.act;
  if (!act) return;
  if (act === "prev") state.day -= 1;
  if (act === "next") state.day += 1;
  if (act === "today") state.day = todayEpoch();
  if (act === "prevWeek") state.weekStart -= 7;
  if (act === "nextWeek") state.weekStart += 7;
  if (act === "thisWeek") state.weekStart = startOfWeek(todayEpoch());
  render();
});
document.addEventListener("keydown", (ev) => {
  if (state.view !== "day" || ev.target.closest("input, textarea")) return;
  if (ev.key === "ArrowLeft") { state.day -= 1; render(); }
  if (ev.key === "ArrowRight") { state.day += 1; render(); }
});

$("#signin").addEventListener("click", async () => {
  const provider = new GoogleAuthProvider();
  try {
    await signInWithPopup(auth, provider);
  } catch (e) {
    if (e.code === "auth/popup-blocked" || e.code === "auth/operation-not-supported-in-this-environment") {
      await signInWithRedirect(auth, provider);
    } else if (e.code !== "auth/popup-closed-by-user" && e.code !== "auth/cancelled-popup-request") {
      const el = $("#login-error"); el.hidden = false; el.textContent = T.notAllowed + (e.code || e.message);
    }
  }
});
$("#signout").addEventListener("click", () => signOut(auth));

// ---------- Sesión y suscripciones en tiempo real ----------
function listen(uid) {
  const base = ["users", uid];
  const sub = (name, map, key) => onSnapshot(collection(db, ...base, name), (snap) => {
    map.clear();
    snap.forEach((d) => { const v = d.data(); map.set(key(v, d.id), v); });
    render();
  });
  unsubscribers = [
    sub("days", state.days, (v) => v.epochDay),
    sub("entries", state.entries, (v) => v.id),
    sub("habits", state.habits, (v) => v.id),
    sub("habitChecks", state.checks, (_, id) => id),
    onSnapshot(doc(db, ...base), (snap) => { state.lastSyncAt = snap.data()?.lastSyncAt || 0; render(); }),
  ];
}

onAuthStateChanged(auth, (user) => {
  unsubscribers.forEach((u) => u()); unsubscribers = [];
  $("#loading").hidden = true;
  $("#login").hidden = !!user;
  $("#app").hidden = !user;
  if (user) {
    $("#who").textContent = user.email || "";
    listen(user.uid);
    render();
  }
});
