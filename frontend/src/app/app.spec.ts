import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Location } from '@angular/common';
import { type ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { App } from './app';
import { appConfig } from './app.config';

/** De app met de echte configuratie en routes (ADR 0012); alleen de HTTP-laag is vervangen. */
describe('App', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<App>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [...appConfig.providers, provideHttpClientTesting()],
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

  const JAN = [{ soort: 'betaal', iban: 'NL94SIMB0000000001', saldo: '3402.60' }];
  const TWEE_DAGEN = {
    transacties: [
      { datum: '2026-10-02', tegenpartij: 'Werkgever B.V.', bedrag: '3215.00' },
      { datum: '2026-10-01', tegenpartij: 'Woningstichting Thuis', bedrag: '-812.40' },
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
        { datum: '2026-10-02', tegenpartij: 'Werkgever B.V.', bedrag: '3215.00' },
        { datum: '2026-10-01', tegenpartij: 'Bakkerij De Korst', bedrag: '-12.50' },
      ],
      volgende: 'MjAyNi0xMC0wMSwy.q-_x',
    });

    knop(pagina, 'Toon meer')?.click();
    await ververs();
    await transacties('/api/rekeningen/NL94SIMB0000000001/transacties?na=MjAyNi0xMC0wMSwy.q-_x', {
      transacties: [{ datum: '2026-10-01', tegenpartij: 'Woningstichting Thuis', bedrag: '-812.40' }],
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
});
