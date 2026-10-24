import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Location } from '@angular/common';
import { type ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { App } from './app';
import { appConfig } from './app.config';
import { Browser } from './browser';

/** De app met de echte configuratie en routes (ADR 0012); alleen de HTTP-laag is vervangen. */
describe('App', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<App>;
  /** De URL's waar de app de browser heen stuurde, buiten de app (ADR 0021). */
  let genavigeerd: string[];

  beforeEach(async () => {
    genavigeerd = [];
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        ...appConfig.providers,
        provideHttpClientTesting(),
        { provide: Browser, useValue: { naar: (url: string) => genavigeerd.push(url) } },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  /** Start de app op `url`, zoals bij het openen of herladen van die pagina. */
  async function start(url: string): Promise<HTMLElement> {
    fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    http.expectOne('/api/versie').flush({ versie: '1.2.3' });
    await TestBed.inject(Router).navigateByUrl(url);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  async function ververs(): Promise<void> {
    await fixture.whenStable();
    fixture.detectChanges();
  }

  async function rekeningen(antwoord: { status: number; body?: object }): Promise<void> {
    const verzoek = http.expectOne('/api/rekeningen');
    if (antwoord.status === 200) {
      verzoek.flush(antwoord.body ?? null);
    } else {
      verzoek.flush(null, { status: antwoord.status, statusText: 'Unauthorized' });
    }
    await ververs();
  }

  async function transacties(pad: string, pagina: object): Promise<void> {
    http.expectOne(pad).flush(pagina);
    await ververs();
  }

  function knop(pagina: HTMLElement, tekst: string): HTMLButtonElement | undefined {
    return [...pagina.querySelectorAll('button')].find((b) => b.textContent?.trim() === tekst);
  }

  function link(pagina: HTMLElement, tekst: string): HTMLAnchorElement | undefined {
    return [...pagina.querySelectorAll('a')].find((a) => a.textContent?.trim() === tekst);
  }

  function datumkoppen(pagina: HTMLElement): (string | undefined)[] {
    return [...(pagina.querySelector('ul[aria-label="Transacties"]')?.children ?? [])].map(
      (dag) => dag.querySelector('h3')?.textContent ?? undefined,
    );
  }

  /** Een transactie zoals bank-api hem levert, met de details van S4.1. */
  function api(datum: string, tegenpartij: string, bedrag: string): object {
    return {
      datum,
      tegenpartij,
      bedrag,
      type: 'ONLINE_BANKIEREN',
      tijdstip: `${datum}T12:00:00+02:00`,
      van: { naam: 'Jan', iban: 'NL94SIMB0000000001' },
      naar: { naam: tegenpartij, iban: null },
    };
  }

  const JAN = [{ soort: 'betaal', iban: 'NL94SIMB0000000001', saldo: '3402.60' }];
  const TWEE_DAGEN = {
    transacties: [
      api('2026-10-02', 'Werkgever B.V.', '3215.00'),
      api('2026-10-01', 'Woningstichting Thuis', '-812.40'),
    ],
    volgende: null,
  };

  it('toont de versie uit de API in de voettekst', async () => {
    const pagina = await start('/');
    await rekeningen({ status: 401 });
    expect(pagina.querySelector('footer')?.textContent).toContain('Versie 1.2.3');
  });

  it('toont zonder login een link "Inloggen" naar de BFF', async () => {
    const pagina = await start('/');
    await rekeningen({ status: 401 });
    expect(link(pagina, 'Inloggen')?.getAttribute('href')).toBe('/oauth2/authorization/keycloak');
    expect(pagina.querySelector('h2')).toBeNull();
  });

  it('toont na inloggen het overzicht met de betaalrekening, IBAN en saldo', async () => {
    const pagina = await start('/');
    await rekeningen({ status: 200, body: [{ soort: 'betaal', iban: 'NL94SIMB0000000001', saldo: '1842.17' }] });
    expect(pagina.querySelector('h2')?.textContent).toMatch(/^Welkom/);
    const lijst = pagina.querySelector('ul[aria-label="Betaalrekeningen"]');
    const regels = [...(lijst?.querySelectorAll('li') ?? [])].map((li) => li.textContent?.replace(/\s+/g, ' ').trim());
    expect(regels).toEqual(['NL94SIMB0000000001 € 1.842,17']);
    expect(link(pagina, 'Inloggen')).toBeUndefined();
  });

  it('opent de betaalrekening op een eigen URL als je op het IBAN klikt (S2.1, S2a.1)', async () => {
    const pagina = await start('/');
    await rekeningen({ status: 200, body: JAN });
    knop(pagina, 'NL94SIMB0000000001')?.click();
    await ververs();
    await transacties('/api/rekeningen/NL94SIMB0000000001/transacties', TWEE_DAGEN);

    expect(TestBed.inject(Location).path()).toBe('/rekeningen/NL94SIMB0000000001');
    expect(pagina.querySelector('h2')?.textContent).toBe('Betaalrekening');
    expect(datumkoppen(pagina)).toEqual(['Vrijdag 2 oktober 2026', 'Donderdag 1 oktober 2026']);
  });

  it('laadt de betaalrekening direct vanaf zijn URL, zoals na herladen (S2a.1)', async () => {
    const pagina = await start('/rekeningen/NL94SIMB0000000001');
    await transacties('/api/rekeningen/NL94SIMB0000000001/transacties', TWEE_DAGEN);

    expect(pagina.querySelector('h2')?.textContent).toBe('Betaalrekening');
    expect(datumkoppen(pagina)).toEqual(['Vrijdag 2 oktober 2026', 'Donderdag 1 oktober 2026']);
  });

  it('gaat met de link "Overzicht" terug naar het overzicht en laadt de rekeningen opnieuw (S2a.1)', async () => {
    const pagina = await start('/rekeningen/NL94SIMB0000000001');
    await transacties('/api/rekeningen/NL94SIMB0000000001/transacties', TWEE_DAGEN);

    link(pagina, 'Overzicht')?.click();
    await ververs();
    await rekeningen({ status: 200, body: JAN });

    expect(TestBed.inject(Location).path()).toBe('');
    expect(pagina.querySelector('h2')?.textContent).toMatch(/^Welkom/);
  });

  it('stuurt een onbekend pad naar het overzicht', async () => {
    await start('/bestaat-niet');
    await rekeningen({ status: 200, body: JAN });
    expect(TestBed.inject(Location).path()).toBe('');
  });

  /** De grens tussen de pagina's valt midden in 1 oktober: die datum mag maar één kop krijgen. */
  it('"Toon meer" vraagt de volgende pagina met de cursor en voegt die toe onder dezelfde datumkop (S2.2)', async () => {
    const pagina = await start('/rekeningen/NL94SIMB0000000001');
    await transacties('/api/rekeningen/NL94SIMB0000000001/transacties', {
      transacties: [
        api('2026-10-02', 'Werkgever B.V.', '3215.00'),
        api('2026-10-01', 'Bakkerij De Korst', '-12.50'),
      ],
      volgende: 'MjAyNi0xMC0wMSwy.q-_x',
    });

    knop(pagina, 'Toon meer')?.click();
    await ververs();
    await transacties('/api/rekeningen/NL94SIMB0000000001/transacties?na=MjAyNi0xMC0wMSwy.q-_x', {
      transacties: [api('2026-10-01', 'Woningstichting Thuis', '-812.40')],
      volgende: null,
    });

    expect(datumkoppen(pagina)).toEqual(['Vrijdag 2 oktober 2026', 'Donderdag 1 oktober 2026']);
    const dagen = [...(pagina.querySelector('ul[aria-label="Transacties"]')?.children ?? [])];
    expect(dagen.map((dag) => dag.querySelectorAll('li').length)).toEqual([1, 2]);
    expect(knop(pagina, 'Toon meer')).toBeUndefined();
  });

  it('toont de link "Inloggen" als de sessie op de betaalrekening verlopen is', async () => {
    const pagina = await start('/rekeningen/NL94SIMB0000000001');
    http
      .expectOne('/api/rekeningen/NL94SIMB0000000001/transacties')
      .flush(null, { status: 401, statusText: 'Unauthorized' });
    await ververs();
    expect(link(pagina, 'Inloggen')).toBeDefined();
  });

  /** S5.1: het zoekformulier openen, velden invullen en "Zoeken" kiezen. */
  async function zoek(pagina: HTMLElement, velden: Record<string, string>): Promise<void> {
    if (knop(pagina, 'Zoeken in transacties')) {
      knop(pagina, 'Zoeken in transacties')?.click();
      await ververs();
    }
    for (const [label, waarde] of Object.entries(velden)) {
      const element = [...pagina.querySelectorAll('label')].find((l) => l.textContent?.trim() === label);
      const invoer = pagina.querySelector(`#${element?.htmlFor}`) as HTMLInputElement;
      invoer.value = waarde;
      invoer.dispatchEvent(new Event('input'));
      await ververs();
    }
    knop(pagina, 'Zoeken')?.click();
    await ververs();
  }

  const PAD = '/api/rekeningen/NL94SIMB0000000001/transacties';

  it('zoekt met de ingevulde velden en vervangt de lijst door wat gevonden is (S5.1)', async () => {
    const pagina = await start('/rekeningen/NL94SIMB0000000001');
    await transacties(PAD, TWEE_DAGEN);

    await zoek(pagina, { 'Naam of IBAN': 'woning', 'Bedrag van (€)': '27,95' });
    await transacties(`${PAD}?tekst=woning&van=27,95`, {
      transacties: [api('2026-10-01', 'Woningstichting Thuis', '-812.40')],
      volgende: null,
    });

    expect(datumkoppen(pagina)).toEqual(['Donderdag 1 oktober 2026']);
  });

  it('"Toon meer" na zoeken stuurt de zoekvelden mee met de cursor (S5.1)', async () => {
    const pagina = await start('/rekeningen/NL94SIMB0000000001');
    await transacties(PAD, TWEE_DAGEN);
    await zoek(pagina, { 'Naam of IBAN': 'albert' });
    await transacties(`${PAD}?tekst=albert`, {
      transacties: [api('2026-10-04', 'Albert Heijn Online', '-27.95')],
      volgende: 'MjAyNi0xMC0wNCw0.q-_x',
    });

    knop(pagina, 'Toon meer')?.click();
    await ververs();
    await transacties(`${PAD}?tekst=albert&na=MjAyNi0xMC0wNCw0.q-_x`, {
      transacties: [api('2026-10-03', 'Albert Heijn', '-63.48')],
      volgende: null,
    });

    expect(datumkoppen(pagina)).toEqual(['Zondag 4 oktober 2026', 'Zaterdag 3 oktober 2026']);
  });

  it('meldt "Geen transacties gevonden." als een zoekopdracht niets vindt (S5.1)', async () => {
    const pagina = await start('/rekeningen/NL94SIMB0000000001');
    await transacties(PAD, TWEE_DAGEN);
    await zoek(pagina, { 'Naam of IBAN': 'Jumbo' });
    await transacties(`${PAD}?tekst=Jumbo`, { transacties: [], volgende: null });

    expect(pagina.textContent).toContain('Geen transacties gevonden.');
    expect(pagina.textContent).not.toContain('Nog geen af- of bijschrijvingen.');
  });

  it('toont de melding van de API bij een ongeldige zoekopdracht (S5.1)', async () => {
    const pagina = await start('/rekeningen/NL94SIMB0000000001');
    await transacties(PAD, TWEE_DAGEN);
    await zoek(pagina, { 'Bedrag van (€)': '100', 'Bedrag t/m (€)': '50' });
    http.expectOne(`${PAD}?van=100&tm=50`).flush(
      {
        type: 'urn:banksim:probleem:ongeldig-bedragbereik',
        detail: 'Bedrag van mag niet groter zijn dan Bedrag t/m',
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await ververs();

    expect(pagina.querySelector('[role="alert"]')?.textContent?.trim()).toBe(
      'Bedrag van mag niet groter zijn dan Bedrag t/m',
    );
  });

  it('"Wissen" laadt weer alle transacties, zonder zoekvelden (S5.1)', async () => {
    const pagina = await start('/rekeningen/NL94SIMB0000000001');
    await transacties(PAD, TWEE_DAGEN);
    await zoek(pagina, { 'Naam of IBAN': 'woning' });
    await transacties(`${PAD}?tekst=woning`, {
      transacties: [api('2026-10-01', 'Woningstichting Thuis', '-812.40')],
      volgende: null,
    });

    knop(pagina, 'Wissen')?.click();
    await ververs();
    await transacties(PAD, TWEE_DAGEN);

    expect(datumkoppen(pagina)).toEqual(['Vrijdag 2 oktober 2026', 'Donderdag 1 oktober 2026']);
  });

  it('meldt "Rekening is niet gevonden." bij een 404, zonder zoekformulier (S6.1)', async () => {
    const pagina = await start('/rekeningen/NL20SIMB0000000002');
    http
      .expectOne('/api/rekeningen/NL20SIMB0000000002/transacties')
      .flush(null, { status: 404, statusText: 'Not Found' });
    await ververs();

    expect(pagina.querySelector('[role="alert"]')?.textContent?.trim()).toBe('Rekening is niet gevonden.');
    expect(knop(pagina, 'Zoeken in transacties')).toBeUndefined();
  });

  it('toont zonder login geen knop "Uitloggen" (S6.2)', async () => {
    const pagina = await start('/');
    await rekeningen({ status: 401 });
    expect(knop(pagina, 'Uitloggen')).toBeUndefined();
  });

  it('"Uitloggen" stuurt een POST naar /logout en gaat naar de uitlog-URL van Keycloak (S6.2, ADR 0021)', async () => {
    const pagina = await start('/');
    await rekeningen({ status: 200, body: JAN });
    const keycloak = 'https://auth-mse.localtest.me/realms/banksim-mse/protocol/openid-connect/logout?id_token_hint=x';

    knop(pagina, 'Uitloggen')?.click();
    http
      .expectOne({ method: 'POST', url: '/logout' })
      .flush(null, { status: 202, statusText: 'Accepted', headers: { Location: keycloak } });
    await ververs();

    expect(genavigeerd).toEqual([keycloak]);
  });
  /** Vult het veld met dit label in, zoals een gebruiker typt. */
  function vulIn(pagina: HTMLElement, label: string, waarde: string): void {
    const id = [...pagina.querySelectorAll('label')].find((l) => l.textContent?.trim() === label)?.htmlFor;
    const veld = id ? pagina.querySelector<HTMLInputElement>(`#${id}`) : null;
    if (!veld) throw new Error(`Geen veld met label ${label}`);
    veld.value = waarde;
    veld.dispatchEvent(new Event('input'));
  }

  /** S7.1 (FO "Betalen scherm"): invullen, "Volgende", het controlescherm, "Bevestigen", terug naar de rekening. */
  it('betaalt vanaf de betaalrekening na het controlescherm en laadt daarna de rekening opnieuw (S7.1)', async () => {
    const pagina = await start('/rekeningen/NL94SIMB0000000001');
    await transacties('/api/rekeningen/NL94SIMB0000000001/transacties', TWEE_DAGEN);

    link(pagina, 'Betalen')?.click();
    await ververs();
    expect(TestBed.inject(Location).path()).toBe('/rekeningen/NL94SIMB0000000001/betalen');
    vulIn(pagina, 'Bedrag (€)', '25,00');
    vulIn(pagina, 'Naam ontvanger', 'Sanne');
    vulIn(pagina, 'Rekeningnummer (IBAN)', 'NL67SIMB0000000002');
    knop(pagina, 'Volgende')?.click();
    await ververs();

    expect(pagina.querySelector('h2')?.textContent).toBe('Controleer je betaling');
    expect(pagina.textContent).toContain('€ 25,00');
    expect(pagina.textContent).toContain('Sanne');
    expect(pagina.textContent).toContain('NL67SIMB0000000002');
    http.expectNone('/api/betalingen');

    knop(pagina, 'Bevestigen')?.click();
    await ververs();
    const betaling = http.expectOne('/api/betalingen');
    expect(betaling.request.method).toBe('POST');
    expect(betaling.request.body).toEqual({
      van: 'NL94SIMB0000000001',
      naar: 'NL67SIMB0000000002',
      naam: 'Sanne',
      bedrag: '25.00',
    });
    betaling.flush(null, { status: 201, statusText: 'Created' });
    await ververs();

    expect(TestBed.inject(Location).path()).toBe('/rekeningen/NL94SIMB0000000001');
    await transacties('/api/rekeningen/NL94SIMB0000000001/transacties', TWEE_DAGEN);
    expect(pagina.querySelector('h2')?.textContent).toBe('Betaalrekening');
  });
});
