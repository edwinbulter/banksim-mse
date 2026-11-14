import { readdirSync, readFileSync } from 'node:fs';
import { basename, dirname, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';
import type { TestInfo } from '@playwright/test';
import * as allure from 'allure-js-commons';

/**
 * De labels van het Allure-rapport (ADR 0053). Ze komen hier vandaan en niet uit de specs: een spec spreekt alleen de
 * DSL (ADR 0031). De automatische fixture allureLabels in fixtures.ts roept labelVoorAllure aan bij elk scenario.
 */

const REPO = join(dirname(fileURLToPath(import.meta.url)), '..', '..');
const GITHUB = 'https://github.com/edwinbulter/banksim-mse/blob';

/** Per specbestand de slice (doc/werkwijze-mse.md, hoofdstuk 7) of het hoofdstuk over de infrastructuur (9). */
const EPICS: Record<string, string> = {
  skelet: 'Stap 0 – Walking skeleton',
  overzicht: 'Slice 1 – Overzicht met saldo',
  betaalrekening: 'Slice 2 – Transactielijst van de betaalrekening',
  navigatie: 'Slice 2a – Navigatie',
  simulatiedatum: 'Slice 3 – Simulatiedatum',
  zoeken: 'Slice 5 – Zoeken in transacties',
  inloggen: 'Slice 6 – Inloggen en rollen',
  toegang: 'Slice 6 – Inloggen en rollen',
  betalen: 'Slice 7 – Betalen',
  spaarrekening: 'Slice 8 – Spaarrekening en rente',
  overschrijven: 'Slice 9 – Overschrijven (inleggen en opnemen)',
  beheer: 'Slice 10 – Admin',
  huishoudens: 'Slice 11 – Realistische data',
  installatie: 'Infrastructuur – Installeren en upgraden',
  upgrade: 'Infrastructuur – Installeren en upgraden',
  cookies: 'Beveiliging – OWASP Top 10',
  tokens: 'Beveiliging – OWASP Top 10',
  keycloak: 'Beveiliging – OWASP Top 10',
};

/** Hoe het scenario het systeem aanspreekt: de driver van het project. */
const LAGEN: Record<string, string> = { rest: 'api', ui: 'ui' };

export async function labelVoorAllure(testInfo: TestInfo, driver: 'rest' | 'ui' | 'shell'): Promise<void> {
  const bestand = basename(testInfo.file, '.spec.ts');
  const describes = testInfo.titlePath.slice(1, -1);

  await allure.epic(EPICS[bestand] ?? bestand);
  await allure.feature(bestand);
  if (describes.length > 0) await allure.story(describes.join(' › '));
  await allure.severity(ernst(testInfo.tags));
  await allure.layer(LAGEN[driver] ?? 'installatie');
  // Het project zet allure-playwright er zelf al bij, als parameter Project.
  await allure.parameter('driver', driver);

  const versie = process.env.GITHUB_SHA ?? 'main';
  const pad = relative(REPO, testInfo.file);
  await allure.link(`${GITHUB}/${versie}/${pad}`, `Spec ${basename(pad)}`);
  for (const adr of adrsIn(testInfo.file)) await allure.link(`${GITHUB}/${versie}/doc/adr/${adr}`, `ADR ${adr.slice(0, 4)}`);
}

/** @rook is het hele pad met inloggen (ADR 0019); @destructief installeert of verwijdert de bank. */
function ernst(tags: string[]): string {
  if (tags.includes('@destructief')) return allure.Severity.BLOCKER;
  if (tags.includes('@rook')) return allure.Severity.CRITICAL;
  return allure.Severity.NORMAL;
}

/** De ADR's die de spec noemt ("ADR 0008"), als bestandsnaam in doc/adr. */
function adrsIn(spec: string): string[] {
  const nummers = new Set(readFileSync(spec, 'utf-8').match(/(?<=ADR )\d{4}/g) ?? []);
  return readdirSync(join(REPO, 'doc', 'adr')).filter((naam) => nummers.has(naam.slice(0, 4)));
}
