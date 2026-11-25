// Tests de la web con Playwright y Firebase simulado. Ejecutar: `npm test` (dentro de web/).
// En CI se usa el Chrome que ya trae el runner (PW_CHANNEL=chrome) para no descargar navegadores.
import { test, before, after } from "node:test";
import assert from "node:assert/strict";
import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";
import { mockModules, sampleData } from "./firebase-mock.mjs";

const require = createRequire(import.meta.url);
const { chromium } = require("playwright");

const WEB_DIR = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const TYPES = { ".html": "text/html", ".js": "text/javascript", ".mjs": "text/javascript", ".css": "text/css", ".svg": "image/svg+xml" };

let server, baseUrl, browser;

before(async () => {
  // Servidor estático mínimo para la carpeta web/
  server = http.createServer((req, res) => {
    const file = path.join(WEB_DIR, decodeURIComponent(new URL(req.url, "http://x").pathname));
    if (!file.startsWith(WEB_DIR) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) { res.writeHead(404); return res.end(); }
    res.writeHead(200, { "Content-Type": TYPES[path.extname(file)] || "application/octet-stream" });
    fs.createReadStream(file).pipe(res);
  });
  await new Promise((r) => server.listen(0, "127.0.0.1", r));
  baseUrl = `http://127.0.0.1:${server.address().port}`;
  browser = await chromium.launch(process.env.PW_CHANNEL ? { channel: process.env.PW_CHANNEL } : {});
});

after(async () => {
  await browser?.close();
  server?.close();
});

/** Abre la web con datos de ejemplo y devuelve la página, los errores de JS y "hoy". */
async function open({ width = 390, height = 900, colorScheme = "light" } = {}) {
  const { data, today, ym } = sampleData();
  const mods = mockModules(data);
  const page = await browser.newPage({ viewport: { width, height }, colorScheme, locale: "es-ES" });
  const errors = [];
  page.on("pageerror", (e) => errors.push(e.message));
  page.on("dialog", (d) => d.accept());
  await page.route("https://www.gstatic.com/firebasejs/**", (route) =>
    route.fulfill({ contentType: "text/javascript", body: mods[route.request().url().split("/").pop()] }));
  await page.goto(`${baseUrl}/index.html`);
  await page.waitForSelector("#app:not([hidden])");
  await page.waitForSelector(".entry");
  return { page, errors, today, ym };
}

const texts = (page, sel) => page.$$eval(sel, (els) => els.map((e) => e.textContent.trim()));

test("muestra el día con sus entradas y oculta las borradas", async () => {
  const { page, errors } = await open();
  const entries = await texts(page, ".entry .text");
  assert.ok(entries.includes("Comprar pan"));
  assert.ok(!entries.includes("borrada"));
  assert.match(await page.textContent("h2"), /Hoy/);
  assert.deepEqual(errors, []);
  await page.close();
});

test("añadir con atajo 'o ' crea un evento", async () => {
  const { page, errors } = await open();
  await page.fill("#add-text", "o Cena con amigos");
  await page.press("#add-text", "Enter");
  await page.waitForFunction(() => [...document.querySelectorAll(".entry .text")].some((e) => e.textContent.includes("Cena con amigos")));
  const row = page.locator(".entry", { hasText: "Cena con amigos" });
  assert.equal((await row.locator(".sym").textContent()).trim(), "○");
  assert.deepEqual(errors, []);
  await page.close();
});

test("ciclar una tarea y migrarla a mañana", async () => {
  const { page, errors } = await open({ width: 1280 });
  await page.click('[data-cycle="1"]'); // • → ×
  await page.click('[data-cycle="1"]'); // × → >
  await page.click('[data-migrate="1"]');
  await page.waitForFunction(() => document.body.textContent.includes("Comprar pan →"));
  await page.click("[data-act=next]");
  await page.waitForFunction(() => [...document.querySelectorAll(".entry .text")].some((e) => e.textContent.trim() === "Comprar pan"));
  assert.deepEqual(errors, []);
  await page.close();
});

test("hábitos: marcar, y editor (añadir, renombrar, reordenar, quitar)", async () => {
  const { page, errors } = await open({ width: 1280 });
  await page.click('[data-habit="2"]');
  await page.waitForFunction(() => document.querySelector('[data-habit="2"]').classList.contains("on"));
  await page.click("[data-act=editHabits]");
  await page.fill("#habit-new", "Leer 20 min");
  await page.press("#habit-new", "Enter");
  await page.fill("#hname-3", "Duolingo irlandés");
  await page.press("#hname-3", "Tab");
  await page.click('[data-hmove="4"][data-dir="-1"]');
  await page.click('[data-harchive="2"]');
  await page.waitForFunction(() => document.querySelectorAll("#habits-edit li").length === 4);
  assert.deepEqual(await page.$$eval("#habits-edit input", (els) => els.map((e) => e.value)),
    ["Creatina", "Ejercicio", "Duolingo irlandés", "Leer 20 min"]);
  await page.click("#habits-close");
  assert.deepEqual(await texts(page, ".chips .chip"), ["✓ Creatina", "Ejercicio", "✓ Duolingo irlandés", "Leer 20 min"]);
  assert.deepEqual(errors, []);
  await page.close();
});

test("tiempo y ánimo: elegir y quitar", async () => {
  const { page, errors } = await open();
  await page.click("[data-weather=rain]");
  await page.click('[data-mood="4"]');
  await page.waitForFunction(() => document.querySelector("[data-weather=rain]").classList.contains("on")
    && document.querySelector('[data-mood="4"]').classList.contains("on"));
  await page.click("[data-weather=rain]"); // tocar otra vez lo quita
  await page.waitForFunction(() => !document.querySelector("[data-weather=rain]").classList.contains("on"));
  assert.deepEqual(errors, []);
  await page.close();
});

test("mes y registro futuro: añadir y pasar al mes siguiente", async () => {
  const { page, errors } = await open({ width: 1280 });
  await page.click(".tab[data-view=month]");
  await page.fill(".month-tasks input", "o Viaje a Galicia");
  await page.press(".month-tasks input", "Enter");
  await page.waitForFunction(() => document.body.textContent.includes("Viaje a Galicia"));
  assert.equal(await page.inputValue(".month-tasks input"), "", "el campo se vacía al añadir");
  await page.hover('[data-mnext="1"]');
  await page.click('[data-mnext="1"]');
  await page.click("[data-act=modeFuture]");
  await page.waitForSelector(".future-grid");
  const firstMonth = await page.textContent(".future-grid .card");
  assert.match(firstMonth, /Renovar pasaporte/);
  assert.ok(await page.$(".trk-grid") === null, "el tracker solo está en la vista Mes");
  assert.deepEqual(errors, []);
  await page.close();
});

test("semana, pendientes y diseño de escritorio", async () => {
  const { page, errors } = await open({ width: 1440, colorScheme: "dark" });
  assert.ok(await page.isVisible("#sidebar"), "barra lateral visible en escritorio");
  await page.click(".tab[data-view=week]");
  await page.waitForSelector(".week-days");
  assert.equal(await page.$$eval(".week-days .card", (c) => c.length), 7);
  await page.click(".tab[data-view=pending]");
  assert.ok((await page.$$eval(".pending-grid .card", (c) => c.length)) >= 1);
  assert.deepEqual(errors, []);
  await page.close();
});

test("en el móvil no hay barra lateral", async () => {
  const { page, errors } = await open({ width: 390 });
  assert.ok(!(await page.isVisible("#sidebar")));
  assert.ok(await page.isVisible("#add-bar"));
  assert.deepEqual(errors, []);
  await page.close();
});
