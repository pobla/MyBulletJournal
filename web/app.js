// Cuaderno de bolsillo — web.
// Lee y escribe en tiempo real en Firestore: users/{uid}/days|entries|habits|habitChecks.
// La app del móvil baja estos cambios al abrirse (gana el cambio más reciente).
import { initializeApp } from "https://www.gstatic.com/firebasejs/13.2.0/firebase-app.js";
import {
  getAuth, onAuthStateChanged, GoogleAuthProvider, signInWithPopup, signInWithRedirect, signOut,
} from "https://www.gstatic.com/firebasejs/13.2.0/firebase-auth.js";
import {
  getFirestore, collection, doc, onSnapshot, setDoc, updateDoc,
} from "https://www.gstatic.com/firebasejs/13.2.0/firebase-firestore.js";

import { WX_ICONS, MOOD_ICONS, MOOD_COLORS } from "./trackers.js";

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
    signIn: "Entrar con Google", signOut: "Salir", tabDay: "Día", tabWeek: "Semana", tabMonth: "Mes", tabPending: "Pendientes",
    monthMode: "Mes", futureMode: "Futuro", futureTitle: "Registro futuro", monthTasks: "Tareas del mes",
    calendar: "Calendario", monthEmpty: "Nada todavía", trackers: "Tiempo y ánimo", tracking: "Seguimiento",
    weather: "Tiempo", mood: "Ánimo",
    wx: { sun: "Sol", partly: "Sol y nubes", cloud: "Nublado", rain: "Lluvia", storm: "Tormenta", wind: "Viento", snow: "Nieve" },
    moods: { 5: "Genial", 4: "Bien", 3: "Normal", 2: "Regular", 1: "Mal" }, toNextMonth: (m) => `Pasar a ${m.toLowerCase()}`,
    today: "Hoy", thisWeek: "Esta semana", week: "Semana", habits: "Hábitos", summary: "Frase del día", log: "Registro",
    days: "Días", nothing: "Nada apuntado", noHabits: "Sin hábitos",
    statusDone: "Pasado al cuaderno ✓", statusChanged: "Cambios sin pasar", statusPending: "Pendiente de pasar al cuaderno",
    pillDone: "✓ Pasado", pillChanged: "Cambios", pillPending: "Pendiente",
    pendingTitle: "Pendientes de pasar", allDone: "Todo pasado al cuaderno ✓",
    entries: (n) => `${n} ${n === 1 ? "entrada" : "entradas"}`, habitsN: (n) => `${n} ${n === 1 ? "hábito" : "hábitos"}`,
    more: (n) => `y ${n} más…`, lastSync: (s) => `Última copia: ${s}`, noSync: "Aún sin copia",
    notAllowed: "No se pudo entrar: ", prev: "Anterior", next: "Siguiente",
    markDone: "Ya lo pasé", unmark: "Desmarcar", summaryPh: "Una frase para resumir el día",
    types: { TASK: "Tarea", EVENT: "Evento", NOTE: "Nota" }, addPh: (t) => `${t}…  (o evento · - nota)`,
    toTomorrow: "→ Mañana", del: "Borrar", saveError: "No se pudo guardar: ", prevMonth: "Mes anterior", nextMonth: "Mes siguiente",
  },
  en: {
    appName: "Pocket Notebook", loginHint: "Your notebook, backed up from your phone. Only your account can see it.",
    signIn: "Sign in with Google", signOut: "Sign out", tabDay: "Day", tabWeek: "Week", tabMonth: "Month", tabPending: "To copy",
    monthMode: "Month", futureMode: "Future log", futureTitle: "Future log", monthTasks: "This month",
    calendar: "Calendar", monthEmpty: "Nothing yet", trackers: "Weather & mood", tracking: "Tracking",
    weather: "Weather", mood: "Mood",
    wx: { sun: "Sunny", partly: "Sun and clouds", cloud: "Cloudy", rain: "Rain", storm: "Storm", wind: "Windy", snow: "Snow" },
    moods: { 5: "Great", 4: "Good", 3: "Okay", 2: "Meh", 1: "Bad" }, toNextMonth: (m) => `Move to ${m}`,
    today: "Today", thisWeek: "This week", week: "Week", habits: "Habits", summary: "Line of the day", log: "Log",
    days: "Days", nothing: "Nothing noted", noHabits: "No habits",
    statusDone: "Copied to the notebook ✓", statusChanged: "Changes not copied yet", statusPending: "Not copied to the notebook yet",
    pillDone: "✓ Copied", pillChanged: "Changes", pillPending: "Pending",
    pendingTitle: "To copy into the notebook", allDone: "Everything is in the notebook ✓",
    entries: (n) => `${n} ${n === 1 ? "entry" : "entries"}`, habitsN: (n) => `${n} ${n === 1 ? "habit" : "habits"}`,
    more: (n) => `and ${n} more…`, lastSync: (s) => `Last backup: ${s}`, noSync: "Not backed up yet",
    notAllowed: "Couldn't sign in: ", prev: "Previous", next: "Next",
    markDone: "Copied it", unmark: "Unmark", summaryPh: "One line to sum up the day",
    types: { TASK: "Task", EVENT: "Event", NOTE: "Note" }, addPh: (t) => `${t}…  (o event · - note)`,
    toTomorrow: "→ Tomorrow", del: "Delete", saveError: "Couldn't save: ", prevMonth: "Previous month", nextMonth: "Next month",
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
  view: "day", day: todayEpoch(), weekStart: startOfWeek(todayEpoch()), cal: null, // cal = {y, m} del mini calendario
  days: new Map(), entries: new Map(), habits: new Map(), checks: new Map(), monthEntries: new Map(), lastSyncAt: 0,
  month: null, monthMode: "month", // month = {y, m} de la vista Mes
};
let unsubscribers = [];

const $ = (sel) => document.querySelector(sel);
const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

// ---------- Iconos de tiempo y ánimo ----------
const MOOD_KEYS = { 5: "great", 4: "good", 3: "ok", 2: "meh", 1: "bad" };
const svgIcon = (inner, size = 24, extra = "") =>
  `<svg viewBox="0 0 24 24" width="${size}" height="${size}" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" ${extra}>${inner}</svg>`;
const wxIcon = (key, size = 24) => WX_ICONS[key] ? svgIcon(WX_ICONS[key], size, `aria-label="${T.wx[key]}"`) : "";
const moodIcon = (level, size = 24) => MOOD_ICONS[MOOD_KEYS[level]] ? svgIcon(MOOD_ICONS[MOOD_KEYS[level]], size, `aria-label="${T.moods[level]}"`) : "";
const moodDot = (level, size = 20) => level
  ? `<span class="mood-dot" style="width:${size}px;height:${size}px;background:${MOOD_COLORS[MOOD_KEYS[level]]}">${moodIcon(level, Math.round(size * 0.8))}</span>` : "";

// ---------- Datos derivados ----------
const entriesOf = (epoch) =>
  [...state.entries.values()].filter((e) => e.epochDay === epoch && !e.deleted)
    .sort((a, b) => a.position - b.position || (a.createdAt || 0) - (b.createdAt || 0));
const activeHabits = () => [...state.habits.values()].filter((h) => !h.archived).sort((a, b) => a.position - b.position);
const doneHabitIds = (epoch) =>
  new Set([...state.checks.values()].filter((c) => c.epochDay === epoch && c.done).map((c) => c.habitId));

function dayStatus(epoch) {
  const d = state.days.get(epoch);
  const hasContent = entriesOf(epoch).length > 0 || doneHabitIds(epoch).size > 0 || (d?.summary || "").trim() !== "" ||
    d?.weather != null || d?.mood != null;
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
function entryList(epoch, limit = Infinity, editable = false) {
  const list = entriesOf(epoch);
  if (!list.length) return `<p class="empty">${T.nothing}</p>`;
  const items = list.slice(0, limit).map((e) => {
    const sym = editable && e.type === "TASK"
      ? `<button class="sym btn-sym" data-cycle="${esc(e._doc)}">${esc(e.symbol)}</button>`
      : `<span class="sym">${esc(e.symbol)}</span>`;
    const del = editable ? `<button class="del" data-del="${esc(e._doc)}" aria-label="${T.del}" title="${T.del}">✕</button>` : "";
    // Migración: "→ 24 nov" si ya se copió; si no, botón para copiarla a mañana.
    const migrated = e.type === "TASK" && e.status === "MIGRATED";
    const mig = e.migratedTo != null
      ? `<span class="mig">→ ${fmt(e.migratedTo, { day: "numeric", month: "short" })}</span>`
      : migrated && editable ? `<button class="mig-btn" data-migrate="${esc(e._doc)}">${T.toTomorrow}</button>` : "";
    return `<li class="entry ${finished(e) ? "finished" : ""} ${isNew(epoch, e.updatedAt) ? "new" : ""}">
       ${sym}<span class="text">${esc(e.text)}${mig ? " " + mig : ""}</span>${del}</li>`;
  }).join("");
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
  const trackersNew = d && isNew(e, d.trackersUpdatedAt || 0);
  return `
    <div class="nav">
      <button class="icon-btn" data-act="prev" aria-label="${T.prev}">‹</button>
      <div class="title"><h2>${isToday ? `${T.today} · ` : ""}${weekdayName(e)}</h2><div class="sub">${longDate(e)}</div></div>
      ${isToday ? "" : `<button class="btn small" data-act="today">${T.today}</button>`}
      <button class="icon-btn" data-act="next" aria-label="${T.next}">›</button>
    </div>
    <div class="day-layout"><section class="day-side">
    ${status ? `<div class="status ${status}"><span>${statusText}${d?.lastTranscribedAt && status === "done" ? ` · <span class="empty">${stamp(d.lastTranscribedAt)}</span>` : ""}</span>
      <button class="btn small ${status === "done" ? "" : "primary"}" data-act="${status === "done" ? "unmark" : "markDone"}">${status === "done" ? T.unmark : T.markDone}</button></div>` : ""}
    <div class="section-title">${T.habits}</div>
    <div class="chips">${habits.length ? habits.map((h) => `<button class="chip ${done.has(h.id) ? "on" : ""}" data-habit="${h.id}">${done.has(h.id) ? "✓ " : ""}${esc(h.name)}</button>`).join("") : `<span class="empty">${T.noHabits}</span>`}</div>
    <div class="section-title">${[T.trackers, d?.weather ? T.wx[d.weather] : null, d?.mood ? T.moods[d.mood] : null].filter(Boolean).join(" · ")}</div>
    <div class="picker ${trackersNew ? "changed" : ""}">${Object.keys(WX_ICONS).map((k) =>
      `<button class="pick wx ${d?.weather === k ? "on" : ""}" data-weather="${k}" title="${T.wx[k]}">${wxIcon(k, 26)}</button>`).join("")}</div>
    <div class="picker mood ${trackersNew ? "changed" : ""}">${[5, 4, 3, 2, 1].map((l) =>
      `<button class="pick md ${d?.mood === l ? "on" : ""}" data-mood="${l}" title="${T.moods[l]}"
         style="--mc:${MOOD_COLORS[MOOD_KEYS[l]]}">${moodIcon(l, 32)}</button>`).join("")}</div>
    <div class="section-title">${T.summary}</div>
    <textarea id="summary" class="summary-input" rows="2" placeholder="${T.summaryPh}">${esc(d?.summary || "")}</textarea>
    </section>
    <section class="day-main">
      <div class="section-title">${T.log}</div>
      <div class="log-card">${entryList(e, Infinity, true)}<div id="add-slot" class="add-slot"></div></div>
    </section></div>`;
}

function renderWeek() {
  const start = state.weekStart;
  const days = [...Array(7).keys()].map((i) => start + i);
  const today = todayEpoch();
  const isCurrent = start === startOfWeek(today);
  const habits = activeHabits();
  const grid = `
    <div class="section-title">${T.tracking}</div>
    <div class="card"><div class="grid">
      <span></span>${days.map((e) => `<span class="h ${e === today ? "today" : ""}">${weekLetter(e)}<br>${toDate(e).getUTCDate()}</span>`).join("")}
      <span class="name muted">${T.weather}</span>${days.map((e) => `<span class="cell">${wxIcon(state.days.get(e)?.weather, 20)}</span>`).join("")}
      <span class="name muted">${T.mood}</span>${days.map((e) => `<span class="cell">${moodDot(state.days.get(e)?.mood, 22)}</span>`).join("")}
      ${habits.map((h) => `<span class="name">${esc(h.name)}</span>${days.map((e) => `<span class="dot ${doneHabitIds(e).has(h.id) ? "on" : ""}"></span>`).join("")}`).join("")}
    </div></div>`;
  const cards = days.map((e) => {
    const d = state.days.get(e);
    return `<div class="card clickable ${e === today ? "today" : ""}" data-open="${e}">
      <div class="wk-head"><strong>${weekdayName(e)}</strong><span class="empty">${fmt(e, { day: "numeric", month: "short" })}</span>${pill(dayStatus(e))}</div>
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
    <div class="section-title">${T.days}</div><div class="week-days">${cards}</div>`;
}

function renderPending() {
  const list = pendingDays();
  if (!list.length) return `<h2>${T.pendingTitle}</h2><p class="empty">${T.allDone}</p>`;
  return `<h2>${T.pendingTitle}</h2><div class="pending-grid">` + list.map((e) => {
    const d = state.days.get(e);
    const parts = [];
    const n = entriesOf(e).length; if (n) parts.push(T.entries(n));
    const h = doneHabitIds(e).size; if (h) parts.push(T.habitsN(h));
    return `<div class="card clickable" data-open="${e}">
      <div style="display:flex;justify-content:space-between;gap:8px;align-items:center"><strong>${e === todayEpoch() ? `${T.today} · ` : ""}${dayMonth(e)}</strong>${pill(dayStatus(e))}</div>
      <div class="empty">${parts.join(" · ")}</div>
      ${d?.summary ? `<p class="summary" style="margin:4px 0 0">“${esc(d.summary)}”</p>` : ""}</div>`;
  }).join("") + "</div>";
}

// ---------- Mes y registro futuro ----------
const ymOf = ({ y, m }) => y * 100 + (m + 1); // m es 0-11 en JS; ym = aaaamm
const ymParts = (ym) => ({ y: Math.floor(ym / 100), m: (ym % 100) - 1 });
const addMonths = ({ y, m }, n) => { const d = new Date(Date.UTC(y, m + n, 1)); return { y: d.getUTCFullYear(), m: d.getUTCMonth() }; };
const monthLabel = (mo, withYear = true) => cap(new Intl.DateTimeFormat(lang, { month: "long", ...(withYear ? { year: "numeric" } : {}), timeZone: "UTC" })
  .format(new Date(Date.UTC(mo.y, mo.m, 1))));
const monthEntriesOf = (ym) => [...state.monthEntries.values()].filter((e) => e.ym === ym && !e.deleted)
  .sort((a, b) => a.position - b.position || (a.createdAt || 0) - (b.createdAt || 0));

function monthEntryList(ym) {
  const list = monthEntriesOf(ym);
  const items = list.map((e) => {
    const sym = e.type === "TASK"
      ? `<button class="sym btn-sym" data-mcycle="${esc(e._doc)}">${esc(e.symbol)}</button>` : `<span class="sym">${esc(e.symbol)}</span>`;
    const mig = e.migratedTo != null
      ? `<span class="mig">→ ${monthLabel(ymParts(e.migratedTo), false)}</span>`
      : `<button class="mig-btn hover-only" data-mnext="${esc(e._doc)}">${T.toNextMonth(monthLabel(addMonths(ymParts(ym), 1), false))}</button>`;
    return `<li class="entry ${finished(e) ? "finished" : ""}">${sym}<span class="text">${esc(e.text)} ${mig}</span>
      <button class="del" data-mdel="${esc(e._doc)}" aria-label="${T.del}" title="${T.del}">✕</button></li>`;
  }).join("");
  return `${list.length ? `<ul class="entries">${items}</ul>` : `<p class="empty">${T.monthEmpty}</p>`}
    <form class="mini-add" data-madd="${ym}" autocomplete="off">
      <input id="madd-${ym}" type="text" placeholder="${T.addPh(T.types.TASK)}" enterkeyhint="send">
      <button class="icon-btn send" type="submit" style="width:36px;height:36px;background:var(--primary);color:var(--on-primary)">＋</button>
    </form>`;
}

// Tracker del mes: cuadrícula L–D con el color del ánimo y el símbolo del tiempo.
function monthTracker(mo, first, n) {
  const lead = (toDate(first).getUTCDay() + 6) % 7;
  const today = todayEpoch();
  const head = [...Array(7).keys()].map((i) => `<span class="wd">${weekLetter(startOfWeek(today) + i)}</span>`).join("");
  const cells = "<span></span>".repeat(lead) + [...Array(n).keys()].map((i) => {
    const e = first + i;
    const d = state.days.get(e);
    const bg = d?.mood ? `background:${MOOD_COLORS[MOOD_KEYS[d.mood]]};color:#1E1B16` : "";
    return `<button class="trk-cell ${e === today ? "today" : ""}" style="${bg}" data-open="${e}">
      <span class="n">${i + 1}</span><span class="w">${wxIcon(d?.weather, 16)}</span></button>`;
  }).join("");
  const legend = [5, 4, 3, 2, 1].map((l) => `<span class="leg">${moodDot(l, 16)} ${T.moods[l]}</span>`).join("");
  return `<div class="card trk-card"><div class="trk-grid">${head}${cells}</div><div class="legend">${legend}</div></div>`;
}

function renderMonth() {
  const mo = state.month || monthOf(todayEpoch());
  const isMonth = state.monthMode === "month";
  const seg = `<div class="seg"><button class="${isMonth ? "on" : ""}" data-act="modeMonth">${T.monthMode}</button>
    <button class="${isMonth ? "" : "on"}" data-act="modeFuture">${T.futureMode}</button></div>`;
  if (!isMonth) {
    const start = addMonths(monthOf(todayEpoch()), 1);
    const cards = [...Array(6).keys()].map((i) => {
      const m = addMonths(start, i);
      return `<div class="card"><div class="section-title" style="margin-top:0">${monthLabel(m)}</div>${monthEntryList(ymOf(m))}</div>`;
    }).join("");
    return `${seg}<h2>${T.futureTitle}</h2><div class="future-grid">${cards}</div>`;
  }
  const first = Math.floor(Date.UTC(mo.y, mo.m, 1) / DAY_MS);
  const n = new Date(Date.UTC(mo.y, mo.m + 1, 0)).getUTCDate();
  const today = todayEpoch();
  const isCurrent = ymOf(mo) === ymOf(monthOf(today));
  const lines = [...Array(n).keys()].map((i) => {
    const e = first + i;
    const dow = (toDate(e).getUTCDay() + 6) % 7;
    const list = entriesOf(e);
    const events = list.filter((x) => x.type === "EVENT").map((x) => `○ ${esc(x.text)}`).join(" · ");
    const open = list.filter((x) => x.type === "TASK" && x.status === "OPEN").length;
    const d = state.days.get(e);
    const st = dayStatus(e);
    return `<button class="cal-line ${e === today ? "today" : ""}" data-open="${e}">
      <span class="num">${i + 1}<small class="${dow >= 5 ? "we" : ""}">${weekLetter(e)}</small></span>
      <span>${events ? `<div class="ev">${events}</div>` : ""}${d?.summary ? `<div class="sm">“${esc(d.summary)}”</div>` : ""}${open ? `<div class="sm" style="font-style:normal">• ${open}</div>` : ""}</span>
      <span class="line-trk">${wxIcon(d?.weather, 18)}${moodDot(d?.mood, 20)}<span class="dot-s ${st === "pending" ? "pending" : st === "changed" ? "changed" : ""}"></span></span></button>`;
  }).join("");
  return `${seg}
    <div class="nav">
      <button class="icon-btn" data-act="monthPrev" aria-label="${T.prevMonth}">‹</button>
      <div class="title"><h2>${monthLabel(mo)}</h2></div>
      ${isCurrent ? "" : `<button class="btn small" data-act="monthToday">${T.today}</button>`}
      <button class="icon-btn" data-act="monthNext" aria-label="${T.nextMonth}">›</button>
    </div>
    <div class="month-layout">
      <section class="month-tasks"><div class="section-title">${T.monthTasks}</div><div class="card">${monthEntryList(ymOf(mo))}</div></section>
      <section>
        <div class="section-title">${T.trackers}</div>${monthTracker(mo, first, n)}
        <div class="section-title">${T.calendar}</div><div class="cal-list">${lines}</div>
      </section>
    </div>`;
}

// ---------- Barra lateral (escritorio): calendario del mes y pendientes ----------
const desktopQuery = matchMedia("(min-width: 1000px)");
function monthOf(epoch) { const d = toDate(epoch); return { y: d.getUTCFullYear(), m: d.getUTCMonth() }; }
function renderSidebar() {
  if (!desktopQuery.matches) return "";
  const { y, m } = state.cal || monthOf(state.day);
  const first = Math.floor(Date.UTC(y, m, 1) / DAY_MS);
  const daysInMonth = new Date(Date.UTC(y, m + 1, 0)).getUTCDate();
  const lead = (toDate(first).getUTCDay() + 6) % 7; // huecos hasta el lunes
  const today = todayEpoch();
  const pending = new Set(pendingDays());
  const withNotes = new Set([...state.entries.values()].filter((e) => !e.deleted).map((e) => e.epochDay));
  const title = cap(new Intl.DateTimeFormat(lang, { month: "long", year: "numeric", timeZone: "UTC" }).format(toDate(first)));
  const weekdays = [...Array(7).keys()].map((i) => `<span class="wd">${weekLetter(startOfWeek(today) + i)}</span>`).join("");
  const blanks = "<span></span>".repeat(lead);
  const cells = [...Array(daysInMonth).keys()].map((i) => {
    const e = first + i;
    const cls = [
      "cal-day",
      e === today ? "today" : "",
      e === state.day && state.view === "day" ? "selected" : "",
      pending.has(e) ? "pend" : withNotes.has(e) ? "has" : "",
    ].join(" ");
    return `<button class="${cls}" data-open="${e}">${i + 1}</button>`;
  }).join("");
  const list = [...pending].slice(-8).reverse().map((e) =>
    `<button class="side-item" data-open="${e}"><span>${dayMonth(e)}</span>${pill(dayStatus(e))}</button>`).join("");
  return `
    <div class="mini-cal">
      <div class="head">
        <button data-act="calPrev" aria-label="${T.prevMonth}">‹</button><strong>${title}</strong>
        <button data-act="calNext" aria-label="${T.nextMonth}">›</button>
      </div>
      <div class="cal-grid">${weekdays}${blanks}${cells}</div>
    </div>
    ${list ? `<div class="side-list"><div class="section-title">${T.tabPending}</div>${list}</div>` : ""}`;
}

let lastRenderedKey = "";
function render() {
  document.querySelectorAll(".tab").forEach((t) => t.classList.toggle("active", t.dataset.view === state.view));
  const view = $("#view");
  // Si se está escribiendo la frase del día, no la pisamos al refrescar.
  const active = document.activeElement;
  const editingSummary = active && active.id && view.contains(active) && /^(INPUT|TEXTAREA)$/.test(active.tagName)
    ? { id: active.id, value: active.value, start: active.selectionStart, end: active.selectionEnd } : null;
  const bar = $("#add-bar");
  const typing = document.activeElement === $("#add-text");
  $("#app").appendChild(bar); // sacarla antes de reescribir la vista (conserva lo escrito)
  view.innerHTML = { day: renderDay, week: renderWeek, month: renderMonth, pending: renderPending }[state.view]();
  $("#sidebar").innerHTML = renderSidebar();
  const slot = $("#add-slot");
  const inline = desktopQuery.matches && slot;
  if (inline) slot.appendChild(bar);
  if (typing) $("#add-text").focus();
  if (editingSummary && document.getElementById(editingSummary.id)) {
    const el = document.getElementById(editingSummary.id);
    el.value = editingSummary.value; el.focus();
    try { el.setSelectionRange(editingSummary.start, editingSummary.end); } catch (_) {}
  }
  // Animación solo al cambiar de vista o de día, no en cada actualización de datos.
  const key = `${state.view}:${state.day}:${state.weekStart}:${JSON.stringify(state.month)}:${state.monthMode}`;
  if (key !== lastRenderedKey) { view.style.animation = "none"; void view.offsetWidth; view.style.animation = ""; lastRenderedKey = key; }
  const showBar = state.view === "day";
  bar.hidden = !showBar;
  document.body.classList.toggle("with-bar", showBar && !inline);
  updateAddBar();
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
  if (open) { state.day = Number(open.dataset.open); state.view = "day"; state.cal = monthOf(state.day); render(); return; }
  const act = ev.target.closest("[data-act]")?.dataset.act;
  if (!act) return;
  if (act === "prev") state.day -= 1;
  if (act === "next") state.day += 1;
  if (act === "today") state.day = todayEpoch();
  if (["prev", "next", "today"].includes(act)) state.cal = monthOf(state.day);
  if (act === "modeMonth") state.monthMode = "month";
  if (act === "modeFuture") state.monthMode = "future";
  if (act === "monthPrev") state.month = addMonths(state.month || monthOf(todayEpoch()), -1);
  if (act === "monthNext") state.month = addMonths(state.month || monthOf(todayEpoch()), 1);
  if (act === "monthToday") state.month = monthOf(todayEpoch());
  if (act === "calPrev" || act === "calNext") {
    const { y, m } = state.cal || monthOf(state.day);
    const d = new Date(Date.UTC(y, m + (act === "calNext" ? 1 : -1), 1));
    state.cal = { y: d.getUTCFullYear(), m: d.getUTCMonth() };
  }
  if (act === "prevWeek") state.weekStart -= 7;
  if (act === "nextWeek") state.weekStart += 7;
  if (act === "thisWeek") state.weekStart = startOfWeek(todayEpoch());
  if (act === "markDone") return save(setTranscribed(state.day, true));
  if (act === "unmark") return save(setTranscribed(state.day, false));
  render();
});
document.addEventListener("click", (ev) => {
  const cycle = ev.target.closest("[data-cycle]")?.dataset.cycle;
  if (cycle) return save(cycleTask(cycle));
  const mcycle = ev.target.closest("[data-mcycle]")?.dataset.mcycle;
  if (mcycle) return save(cycleMonthEntry(mcycle));
  const mdel = ev.target.closest("[data-mdel]")?.dataset.mdel;
  if (mdel) return save(deleteMonthEntry(mdel));
  const mnext = ev.target.closest("[data-mnext]")?.dataset.mnext;
  if (mnext) return save(moveMonthEntryNext(mnext));
  const mig = ev.target.closest("[data-migrate]")?.dataset.migrate;
  if (mig) return save(migrateTask(mig, state.day + 1));
  const del = ev.target.closest("[data-del]")?.dataset.del;
  if (del) return save(deleteEntry(del));
  const wx = ev.target.closest("[data-weather]")?.dataset.weather;
  if (wx) return save(setWeather(state.day, wx));
  const md = ev.target.closest("[data-mood]")?.dataset.mood;
  if (md) return save(setMood(state.day, Number(md)));
  const habit = ev.target.closest("[data-habit]")?.dataset.habit;
  if (habit) return save(toggleHabit(state.day, Number(habit)));
});
document.addEventListener("submit", (ev) => {
  const form = ev.target.closest("[data-madd]");
  if (!form) return;
  ev.preventDefault();
  const input = form.querySelector("input");
  let text = input.value.trim();
  if (!text) return;
  // Mismos atajos que en el día: "o " evento, "- " nota
  let type = "TASK";
  const hit = SHORTCUTS.find(([p]) => text.startsWith(p));
  if (hit) { type = hit[1]; text = text.slice(hit[0].length).trim(); }
  if (!text) return;
  input.value = ""; // vaciar antes de guardar: el refresco de datos puede llegar enseguida
  save(addMonthEntry(Number(form.dataset.madd), type, text));
});
document.addEventListener("change", (ev) => {
  if (ev.target.id === "summary") save(setSummary(state.day, ev.target.value));
});

// ---------- Barra de añadir (con los atajos de la app: "o " evento, "- " nota) ----------
let addType = "TASK";
const NEXT_TYPE = { TASK: "EVENT", EVENT: "NOTE", NOTE: "TASK" };
const SHORTCUTS = [["o ", "EVENT"], ["O ", "EVENT"], ["○ ", "EVENT"], ["- ", "NOTE"], ["– ", "NOTE"], ["— ", "NOTE"], ["* ", "TASK"], ["• ", "TASK"]];
function updateAddBar() {
  $("#add-type").textContent = TYPE_SYMBOL[addType];
  $("#add-text").placeholder = T.addPh(T.types[addType]);
}
$("#add-type").addEventListener("click", () => { addType = NEXT_TYPE[addType]; updateAddBar(); $("#add-text").focus(); });
$("#add-text").addEventListener("input", (ev) => {
  const v = ev.target.value;
  const hit = SHORTCUTS.find(([p]) => v.startsWith(p));
  if (hit) { addType = hit[1]; ev.target.value = v.slice(hit[0].length); updateAddBar(); }
});
$("#add-bar").addEventListener("submit", (ev) => {
  ev.preventDefault();
  const input = $("#add-text");
  const text = input.value.trim();
  if (!text) return;
  save(addEntry(state.day, addType, text));
  input.value = ""; addType = "TASK"; updateAddBar(); input.focus();
});
desktopQuery.addEventListener("change", () => { if (auth.currentUser) render(); });
document.addEventListener("keydown", (ev) => {
  if (state.view !== "day" || ev.target.closest("input, textarea")) return;
  if (ev.key === "ArrowLeft") { state.day -= 1; state.cal = monthOf(state.day); render(); }
  if (ev.key === "ArrowRight") { state.day += 1; state.cal = monthOf(state.day); render(); }
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

// ---------- Escrituras (la web escribe igual que la app: updatedAt y el día vuelve a pendiente) ----------
const TYPE_SYMBOL = { TASK: "•", EVENT: "○", NOTE: "–" };
const STATUS_SYMBOL = { OPEN: "•", DONE: "×", MIGRATED: ">" };
const NEXT_STATUS = { OPEN: "DONE", DONE: "MIGRATED", MIGRATED: "OPEN" };
const userPath = () => ["users", auth.currentUser.uid];
const isoDate = (epoch) => toDate(epoch).toISOString().slice(0, 10);
const now = () => Date.now();

function save(promise) {
  promise.catch((e) => alert(T.saveError + (e.code || e.message)));
}
function touchDay(epoch, extra = {}) {
  // Cualquier cambio deja el día pendiente de pasar al cuaderno (como en la app).
  return setDoc(doc(db, ...userPath(), "days", String(epoch)),
    { epochDay: epoch, date: isoDate(epoch), transcribed: false, updatedAt: now(), ...extra }, { merge: true });
}
async function addEntry(epoch, type, text) {
  const id = "w_" + crypto.randomUUID().replace(/-/g, "").slice(0, 20);
  const position = Math.max(-1, ...entriesOf(epoch).map((e) => e.position ?? 0)) + 1;
  const t = now();
  await setDoc(doc(db, ...userPath(), "entries", id), {
    epochDay: epoch, date: isoDate(epoch), type, status: "OPEN", symbol: TYPE_SYMBOL[type], text,
    position, createdAt: t, updatedAt: t, deleted: false, origin: "web",
  });
  await touchDay(epoch);
}
async function cycleTask(docId) {
  const e = state.entries.get(docId);
  if (!e || e.type !== "TASK") return;
  const status = NEXT_STATUS[e.status] || "OPEN";
  await updateDoc(doc(db, ...userPath(), "entries", docId), { status, symbol: STATUS_SYMBOL[status], updatedAt: now() });
  await touchDay(e.epochDay);
}
async function migrateTask(docId, toEpoch) {
  const e = state.entries.get(docId);
  if (!e || e.type !== "TASK" || e.migratedTo === toEpoch) return;
  const t = now();
  await updateDoc(doc(db, ...userPath(), "entries", docId),
    { status: "MIGRATED", symbol: STATUS_SYMBOL.MIGRATED, migratedTo: toEpoch, updatedAt: t });
  await addEntry(toEpoch, "TASK", e.text);
  await touchDay(e.epochDay);
}
async function addMonthEntry(ym, type, text) {
  const id = "w_" + crypto.randomUUID().replace(/-/g, "").slice(0, 20);
  const position = Math.max(-1, ...monthEntriesOf(ym).map((e) => e.position ?? 0)) + 1;
  const t = now();
  await setDoc(doc(db, ...userPath(), "monthEntries", id), {
    ym, type, status: "OPEN", symbol: TYPE_SYMBOL[type], text, position, createdAt: t, updatedAt: t, deleted: false, origin: "web",
  });
}
async function cycleMonthEntry(docId) {
  const e = state.monthEntries.get(docId);
  if (!e || e.type !== "TASK") return;
  const status = NEXT_STATUS[e.status] || "OPEN";
  await updateDoc(doc(db, ...userPath(), "monthEntries", docId), { status, symbol: STATUS_SYMBOL[status], updatedAt: now() });
}
async function deleteMonthEntry(docId) {
  await updateDoc(doc(db, ...userPath(), "monthEntries", docId), { deleted: true, updatedAt: now() });
}
async function moveMonthEntryNext(docId) {
  const e = state.monthEntries.get(docId);
  if (!e) return;
  const to = ymOf(addMonths(ymParts(e.ym), 1));
  const data = { migratedTo: to, updatedAt: now() };
  if (e.type === "TASK") Object.assign(data, { status: "MIGRATED", symbol: STATUS_SYMBOL.MIGRATED });
  await updateDoc(doc(db, ...userPath(), "monthEntries", docId), data);
  await addMonthEntry(to, e.type, e.text);
}
async function deleteEntry(docId) {
  const e = state.entries.get(docId);
  if (!e) return;
  await updateDoc(doc(db, ...userPath(), "entries", docId), { deleted: true, updatedAt: now() });
  await touchDay(e.epochDay);
}
async function toggleHabit(epoch, habitId) {
  const done = doneHabitIds(epoch).has(habitId);
  await setDoc(doc(db, ...userPath(), "habitChecks", `${epoch}_${habitId}`),
    { epochDay: epoch, date: isoDate(epoch), habitId, done: !done, updatedAt: now() });
  await touchDay(epoch);
}
// Tiempo y ánimo: tocar el elegido lo quita (null).
async function setWeather(epoch, key) {
  const cur = state.days.get(epoch)?.weather ?? null;
  await touchDay(epoch, { weather: cur === key ? null : key, trackersUpdatedAt: now() });
}
async function setMood(epoch, level) {
  const cur = state.days.get(epoch)?.mood ?? null;
  await touchDay(epoch, { mood: cur === level ? null : level, trackersUpdatedAt: now() });
}
async function setSummary(epoch, text) {
  if ((state.days.get(epoch)?.summary || "") === text) return;
  await touchDay(epoch, { summary: text, summaryUpdatedAt: now() });
}
function setTranscribed(epoch, value) {
  const t = now();
  const data = value
    ? { epochDay: epoch, date: isoDate(epoch), transcribed: true, lastTranscribedAt: t, updatedAt: t }
    : { epochDay: epoch, date: isoDate(epoch), transcribed: false, updatedAt: t };
  return setDoc(doc(db, ...userPath(), "days", String(epoch)), data, { merge: true });
}

// ---------- Sesión y suscripciones en tiempo real ----------
function listen(uid) {
  const base = ["users", uid];
  const sub = (name, map, key) => onSnapshot(collection(db, ...base, name), (snap) => {
    map.clear();
    snap.forEach((d) => { const v = { ...d.data(), _doc: d.id }; map.set(key(v, d.id), v); });
    render();
  });
  unsubscribers = [
    sub("days", state.days, (v) => v.epochDay),
    sub("entries", state.entries, (_, id) => id),
    sub("habits", state.habits, (v) => v.id),
    sub("habitChecks", state.checks, (_, id) => id),
    sub("monthEntries", state.monthEntries, (_, id) => id),
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
