# Stack: Playwright Test in TypeScript met een interne DSL

De vier lagen staan in één map, bijvoorbeeld `e2e/`. Er wordt geen Gherkin gebruikt: de specs zelf zijn het leesbare voorbeeld. De stappen van de DSL zorgen dat het Playwright-rapport elk scenario leest als Gegeven, Als, Dan.

```text
e2e/
├── playwright.config.ts   projecten per driver: rest, chromium, firefox, webkit (+ eventueel infra)
├── specs/                 laag 1: testcases
├── dsl/                   laag 2: fixtures.ts, <ingang>.ts, rollen (klant.ts, bezoeker.ts, …), aliassen.ts
└── drivers/               laag 3: <naam>-driver.ts (interface), rest/, ui/ (page objects), shell/
```

## Laag 1: testcase

```ts
import { test } from '../dsl/fixtures';

test('De startpagina toont de versie', async ({ bank }) => {
  const bezoeker = bank.bezoeker();
  await bank.als('een bezoeker BankSim opent', () => bezoeker.opentBankSim());
  await bank.dan('wordt de versie van BankSim getoond', () => bezoeker.verwachtVersie());
});
```

In een testcase staan alleen domeinwoorden en DSL-aanroepen. Er staan geen URL's, selectors of JSON in.

## Laag 2: DSL

```ts
// dsl/fixtures.ts – kiest per Playwright-project de driver
import { test as basis } from '@playwright/test';

export interface Opties { driver: 'rest' | 'ui'; }

export const test = basis.extend<Opties & { bank: Bank }>({
  driver: ['rest', { option: true }],
  bank: async ({ driver, page, request }, use) => {
    const bankDriver = driver === 'ui' ? new UiDriver(page) : new RestDriver(request);
    await use(new Bank(bankDriver, (titel, stap) => basis.step(titel, stap)));
  },
});
```

```ts
// dsl/bank.ts – ingang plus stappen; de code in een stap roept alleen de DSL aan
export class Bank {
  constructor(private readonly driver: BankDriver, private readonly stap: Stap) {}
  bezoeker(): Bezoeker { return new Bezoeker(this.driver); }
  async gegeven(tekst: string, stap: () => Promise<unknown>) { await this.stap(`Gegeven ${tekst}`, stap); }
  // als, dan, en, maar: hetzelfde patroon
}
```

```ts
// dsl/bezoeker.ts – een rol; de beweringen zitten hier, niet in de driver
export class Bezoeker {
  constructor(private readonly driver: BankDriver) {}
  async opentBankSim() { await this.driver.openStartpagina(); }
  async verwachtVersie() { expect(await this.driver.versie()).toMatch(/^\S+$/); }
}
```

Aliassen: de DSL vertaalt "Jan" naar een unieke naam per testrun, bijvoorbeeld met een runprefix en een teller. De testcase gebruikt alleen de alias.

## Laag 3: drivers

```ts
// drivers/bank-driver.ts – wat de DSL nodig heeft
export interface BankDriver {
  openStartpagina(): Promise<void>;
  versie(): Promise<string>;
}
```

```ts
// drivers/rest/rest-driver.ts – via de publieke ingang, zoals de browser
async versie(): Promise<string> {
  const antwoord = await this.request.get('/api/versie');
  expect(antwoord.status(), 'GET /api/versie').toBe(200);   // protocolcontrole, geen gedragsbewering
  return ((await antwoord.json()) as { versie: string }).versie;
}
```

```ts
// drivers/ui/start-pagina.ts – page object; selecteer op rol en tekst, niet op CSS
async versie(): Promise<string> {
  const voettekst = this.page.getByRole('contentinfo');
  await expect(voettekst).toContainText(/Versie \S+/);
  return ((await voettekst.textContent()) ?? '').replace(/.*Versie\s+/s, '').trim();
}
```

## Configuratie

```ts
// playwright.config.ts (kern)
export default defineConfig<Opties>({
  testDir: './specs',
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: { baseURL: process.env.APP_URL ?? 'https://…', trace: 'retain-on-failure', screenshot: 'only-on-failure' },
  projects: [
    { name: 'rest',     grepInvert: /@wip|@ui/, use: { driver: 'rest' } },
    { name: 'chromium', grepInvert: /@wip/,     use: { ...devices['Desktop Chrome'],  driver: 'ui' } },
    { name: 'firefox',  grepInvert: /@wip/,     use: { ...devices['Desktop Firefox'], driver: 'ui' } },
    { name: 'webkit',   grepInvert: /@wip/,     use: { ...devices['Desktop Safari'],  driver: 'ui' } },
  ],
});
```

## Tips en valkuilen

- **Tags:**
  - `@wip` sluit je overal uit.
  - `@ui` alleen in `rest`.
  - Scenario's die een gedeelde toestand veranderen, krijgen een eigen tag en draaien met `--workers=1`.
- **Het project `rest` start technisch toch een browser** als de fixture `page` in de fixture-functie wordt gevraagd. Dat is onschuldig, maar maakt het iets trager. Wil je dat niet, maak `page` dan lui, via een aparte fixture per driver.
- **Typecheck van de specs** (`tsc --noEmit`) hoort in de commit stage. Dan ontdek je een kapotte DSL al vóór de acceptance stage.
- **Zelfondertekend certificaat** in de testomgeving? Zet `ignoreHTTPSErrors: true` en leg in een ADR vast wanneer het strenger wordt.
- **Browsers in CI:** `npx playwright install --with-deps chromium firefox webkit`. Geef die stap een eigen timeout.
