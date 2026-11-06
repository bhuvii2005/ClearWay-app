/**
 * Browser smoke test: signs up, compares and saves a route, then visits every page.
 * Needs the API (8090), the dev server (5173) and a TomTom key. Screenshots go to e2e/screenshots.
 *   npm run e2e            (set CHROME_PATH if Chrome is not in the default Windows location)
 */
import { chromium } from 'playwright-core';
import fs from 'node:fs';
import path from 'node:path';

const OUT = path.join(import.meta.dirname, 'screenshots');
fs.mkdirSync(OUT, { recursive: true });
const BASE = process.env.E2E_BASE_URL || 'http://localhost:5173';
const problems = [];

const browser = await chromium.launch({
  executablePath: process.env.CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe',
  headless: true,
});
const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
const page = await context.newPage();
page.on('console', (m) => m.type() === 'error' && problems.push(`console: ${m.text()}`));
page.on('pageerror', (e) => problems.push(`pageerror: ${e.message}`));
page.on('response', (r) => {
  if (r.status() >= 400) problems.push(`HTTP ${r.status()} ${r.request().method()} ${r.url()}`);
});

const shot = (name) => page.screenshot({ path: path.join(OUT, `${name}.png`) });
/** Flip the light/dark switch and wait for the map to restyle. */
const flipTheme = async () => {
  await page.getByRole('switch', { name: 'Dark theme' }).click();
  await page.waitForTimeout(2500);
};
const step = (s) => console.log('-', s);

step('redirect to login');
await page.goto(BASE + '/');
await page.waitForURL('**/login');
await page.waitForTimeout(2500); // let the illustration finish drawing
await shot('01-login');

step('signup');
await page.getByRole('link', { name: 'Create an account' }).click();
await page.getByLabel('Name').fill('Asha Verma');
await page.getByLabel('Email').fill(`asha${Date.now()}@test.dev`);
await page.getByLabel('Password').fill('password123');
await page.getByRole('button', { name: 'Create account' }).click();
await page.waitForURL(BASE + '/');
await page.getByRole('heading', { name: 'Where to?' }).waitFor();
await page.waitForTimeout(1500);
await shot('02-planner-empty');

step('search places');
const start = page.getByRole('combobox').nth(0);
await start.fill('India Gate');
await page.getByRole('option').first().waitFor({ timeout: 15000 });
await shot('03-suggestions');
await page.getByRole('option').first().click();
const dest = page.getByRole('combobox').nth(1);
await dest.fill('Lodhi Garden');
await page.getByRole('option').first().waitFor({ timeout: 15000 });
await page.getByRole('option').first().click();

step('compare cycling');
await page.getByText('Cycle', { exact: true }).click();
await page.getByRole('button', { name: 'Compare routes' }).click();
await page.getByRole('heading', { name: /^\d+ routes?/ }).waitFor({ timeout: 30000 });
await page.waitForTimeout(1500);
await shot('04-results');

step('dark theme keeps the route');
await flipTheme();
if ((await page.locator('html').getAttribute('data-theme')) !== 'dark') problems.push('theme switch did not apply dark');
await shot('04b-results-dark');
await flipTheme();
await shot('04c-results-light-again');

step('select second option');
const optionButtons = page.locator('section[aria-label="Route options"] li > div > button');
if ((await optionButtons.count()) > 1) {
  await optionButtons.nth(1).click();
  await page.waitForTimeout(500);
}
await optionButtons.nth(0).click();
await page.waitForTimeout(800); // let the other card finish closing

step('save route');
await page.getByRole('button', { name: 'Save this route' }).click();
await page.getByRole('button', { name: 'Save route' }).click();
await page.getByText('Route saved').waitFor({ timeout: 15000 });
await page.waitForTimeout(2500); // allow a live alert toast to arrive
await shot('05-saved');

step('my routes');
await page.getByRole('link', { name: /My routes/ }).first().click();
await page.getByRole('heading', { name: 'My routes' }).waitFor();
await page.waitForTimeout(800);
await shot('06-routes');

step('route detail');
await page.getByRole('link', { name: /History & best times/ }).first().click();
await page.getByText('Air-quality history').waitFor();
await page.waitForTimeout(2000);
await shot('07-detail');
await flipTheme();
await shot('07b-detail-dark');
await page.reload(); // the choice should survive a reload
await page.getByText('Air-quality history').waitFor();
if ((await page.locator('html').getAttribute('data-theme')) !== 'dark') problems.push('dark theme not remembered after reload');
await flipTheme();

step('alerts');
await page.goto(BASE + '/alerts');
await page.getByRole('heading', { name: 'Alerts', exact: true }).waitFor();
await page.waitForTimeout(1500);
await shot('08-alerts');

step('account');
await page.goto(BASE + '/account');
await page.getByRole('heading', { name: 'Account', exact: true }).waitFor();
await page.getByRole('switch', { name: /PM2.5/ }).click({ force: true });
await page.waitForTimeout(800);
await shot('09-account');

step('mobile planner');
await page.setViewportSize({ width: 390, height: 844 });
await page.goto(BASE + '/');
await page.waitForTimeout(1500);
await shot('10-mobile');
await page.getByRole('button', { name: 'Expand the planner' }).click();
await page.waitForTimeout(600);
await shot('10a-mobile-expanded');
await page.getByRole('button', { name: 'Shrink the planner' }).click();
await flipTheme();
await shot('10b-mobile-dark');
await flipTheme();
await page.goto(BASE + '/routes');
await page.waitForTimeout(1000);
await shot('11-mobile-routes');

step('sign out everywhere');
await page.setViewportSize({ width: 1440, height: 900 });
await page.goto(BASE + '/account');
await page.getByRole('button', { name: 'Sign out everywhere' }).click();
await page.waitForURL('**/login');

await browser.close();
if (problems.length) {
  console.error(`Problems:\n${[...new Set(problems)].join('\n')}`);
  process.exit(1);
}
console.log('Smoke test passed. Screenshots in e2e/screenshots.');
