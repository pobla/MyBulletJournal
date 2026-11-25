// Firebase simulado para los tests: sustituye a los módulos del CDN (gstatic) que importa app.js.
// - Auth: entra directamente como un usuario de prueba.
// - Firestore: en memoria, con lecturas, escrituras y onSnapshot en tiempo real (como el de verdad).

const DAY_MS = 86400000;

/** Datos de ejemplo relativos a "hoy" (epochDay en UTC, igual que la app). */
export function sampleData() {
  const d = new Date();
  const today = Math.floor(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()) / DAY_MS);
  const ym = d.getFullYear() * 100 + d.getMonth() + 1;
  return {
    today,
    ym,
    data: {
      days: [
        { epochDay: today, summary: "Buen día, paseo con Ollie", transcribed: false, lastTranscribedAt: null, updatedAt: 1 },
        { epochDay: today - 1, summary: "", weather: "rain", mood: 2, transcribed: true, lastTranscribedAt: Date.now() - 3600e3, updatedAt: 1 },
        { epochDay: today - 2, summary: "Cansado", weather: "wind", mood: 3, transcribed: false, lastTranscribedAt: Date.now() - 3 * DAY_MS, updatedAt: Date.now() },
      ],
      entries: [
        { id: 1, epochDay: today, type: "TASK", status: "OPEN", symbol: "•", text: "Comprar pan", position: 0, updatedAt: 1, deleted: false },
        { id: 2, epochDay: today, type: "EVENT", status: "OPEN", symbol: "○", text: "Dentista a las 10", position: 1, updatedAt: 1, deleted: false },
        { id: 3, epochDay: today, type: "TASK", status: "DONE", symbol: "×", text: "Llamar al banco", position: 2, updatedAt: 1, deleted: false },
        { id: 4, epochDay: today, type: "NOTE", status: "OPEN", symbol: "–", text: "Idea: regalo para Ollie", position: 3, updatedAt: 1, deleted: false },
        { id: 5, epochDay: today - 2, type: "TASK", status: "OPEN", symbol: "•", text: "Revisar Home Assistant", position: 0, updatedAt: Date.now(), deleted: false },
        { id: 6, epochDay: today, type: "TASK", status: "OPEN", symbol: "•", text: "borrada", position: 4, updatedAt: 1, deleted: true },
      ],
      habits: [
        { id: 1, name: "Creatina", position: 0, archived: false },
        { id: 2, name: "Vitamina D", position: 1, archived: false },
        { id: 3, name: "Duolingo", position: 2, archived: false },
        { id: 4, name: "Ejercicio", position: 3, archived: false },
      ],
      habitChecks: [
        { epochDay: today, habitId: 1, done: true },
        { epochDay: today, habitId: 3, done: true },
      ],
      monthEntries: [
        { id: 1, ym, type: "TASK", status: "OPEN", symbol: "•", text: "Renovar pasaporte", position: 0, deleted: false },
      ],
    },
  };
}

/** Código fuente de los tres módulos simulados, listo para servirlos con page.route(). */
export function mockModules(data) {
  return {
    "firebase-app.js": "export function initializeApp() { return {}; }",
    "firebase-auth.js": `
      export function getAuth() { return {}; }
      export function onAuthStateChanged(auth, cb) {
        setTimeout(() => { auth.currentUser = { uid: "u", email: "test@example.com" }; cb(auth.currentUser); }, 30);
      }
      export class GoogleAuthProvider {}
      export async function signInWithPopup() {}
      export async function signInWithRedirect() {}
      export async function signOut() {}`,
    "firebase-firestore.js": `
      const DATA = ${JSON.stringify(data)};
      const docId = (name, v, i) => name === "habitChecks" ? v.epochDay + "_" + v.habitId
        : name === "days" ? String(v.epochDay) : String(v.id ?? i);
      const STORE = {}, LISTENERS = {};
      for (const [name, rows] of Object.entries(DATA)) {
        STORE[name] = new Map(rows.map((v, i) => [docId(name, v, i), v]));
      }
      function emit(name) {
        (LISTENERS[name] || []).forEach((cb) => cb({ forEach: (f) => STORE[name].forEach((v, id) => f({ id, data: () => v })) }));
      }
      export function getFirestore() { return {}; }
      export function collection(db, ...path) { return path.join("/"); }
      export function doc(db, ...path) { return path.join("/"); }
      export function onSnapshot(ref, cb) {
        const parts = ref.split("/"); const name = parts.pop();
        if (parts.length < 2) { setTimeout(() => cb({ data: () => ({ lastSyncAt: Date.now() - 120000 }) }), 20); return () => {}; }
        (LISTENERS[name] ||= []).push(cb); setTimeout(() => emit(name), 20); return () => {};
      }
      export async function setDoc(ref, data, opts) {
        const p = ref.split("/"); const id = p.pop(); const name = p.pop();
        STORE[name] ||= new Map();
        const prev = STORE[name].get(id) || {};
        STORE[name].set(id, opts && opts.merge ? { ...prev, ...data } : data);
        window.__writes = (window.__writes || 0) + 1;
        emit(name);
      }
      export async function updateDoc(ref, data) { return setDoc(ref, data, { merge: true }); }`,
  };
}
