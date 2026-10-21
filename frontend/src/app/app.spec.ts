import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { type ComponentFixture, TestBed } from '@angular/core/testing';
import { App } from './app';

describe('App', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<App>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  async function start(rekeningen: { status: number; body?: object }): Promise<HTMLElement> {
    fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    http.expectOne('/api/versie').flush({ versie: '1.2.3' });
    const verzoek = http.expectOne('/api/rekeningen');
    if (rekeningen.status === 200) {
      verzoek.flush(rekeningen.body ?? null);
    } else {
      verzoek.flush(null, { status: rekeningen.status, statusText: 'Unauthorized' });
    }
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('toont de versie uit de API in de voettekst', async () => {
    const pagina = await start({ status: 401 });
    expect(pagina.querySelector('footer')?.textContent).toContain('Versie 1.2.3');
  });

  it('toont zonder login een link "Inloggen" naar de BFF', async () => {
    const pagina = await start({ status: 401 });
    const link = [...pagina.querySelectorAll('a')].find((a) => a.textContent?.trim() === 'Inloggen');
    expect(link?.getAttribute('href')).toBe('/oauth2/authorization/keycloak');
    expect(pagina.querySelector('h2')).toBeNull();
  });

  it('toont na inloggen het overzicht met de betaalrekening, IBAN en saldo', async () => {
    const pagina = await start({
      status: 200,
      body: [{ soort: 'betaal', iban: 'NL94SIMB0000000001', saldo: '1842.17' }],
    });
    expect(pagina.querySelector('h2')?.textContent).toMatch(/^Welkom/);
    const lijst = pagina.querySelector('ul[aria-label="Betaalrekeningen"]');
    const regels = [...(lijst?.querySelectorAll('li') ?? [])].map((li) => li.textContent?.replace(/\s+/g, ' ').trim());
    expect(regels).toEqual(['NL94SIMB0000000001 € 1.842,17']);
    expect([...pagina.querySelectorAll('a')].some((a) => a.textContent?.trim() === 'Inloggen')).toBe(false);
  });

  it('opent de betaalrekening met de transacties per datum als je op het IBAN klikt (S2.1)', async () => {
    const pagina = await start({
      status: 200,
      body: [{ soort: 'betaal', iban: 'NL94SIMB0000000001', saldo: '3402.60' }],
    });
    const knop = [...pagina.querySelectorAll('button')].find((b) => b.textContent?.trim() === 'NL94SIMB0000000001');
    expect(knop).toBeDefined();
    knop?.click();
    fixture.detectChanges();
    http.expectOne('/api/rekeningen/NL94SIMB0000000001/transacties').flush({
      transacties: [
        { datum: '2026-10-02', tegenpartij: 'Werkgever B.V.', bedrag: '3215.00' },
        { datum: '2026-10-01', tegenpartij: 'Woningstichting Thuis', bedrag: '-812.40' },
      ],
      volgende: null,
    });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(pagina.querySelector('h2')?.textContent).toBe('Betaalrekening');
    const dagen = [...(pagina.querySelector('ul[aria-label="Transacties"]')?.children ?? [])];
    expect(dagen.map((dag) => dag.querySelector('h3')?.textContent)).toEqual([
      'Vrijdag 2 oktober 2026',
      'Donderdag 1 oktober 2026',
    ]);
    expect(dagen.map((dag) => [...dag.querySelectorAll('li')].map((li) => li.textContent?.replace(/\s+/g, ' ').trim()))).toEqual([
      ['Werkgever B.V. +3.215,00'],
      ['Woningstichting Thuis −812,40'],
    ]);
  });

  /** De grens tussen de pagina's valt midden in 1 oktober: die datum mag maar één kop krijgen. */
  it('"Toon meer" vraagt de volgende pagina met de cursor en voegt die toe onder dezelfde datumkop (S2.2)', async () => {
    const pagina = await start({
      status: 200,
      body: [{ soort: 'betaal', iban: 'NL94SIMB0000000001', saldo: '3402.60' }],
    });
    [...pagina.querySelectorAll('button')].find((b) => b.textContent?.trim() === 'NL94SIMB0000000001')?.click();
    fixture.detectChanges();
    http.expectOne('/api/rekeningen/NL94SIMB0000000001/transacties').flush({
      transacties: [
        { datum: '2026-10-02', tegenpartij: 'Werkgever B.V.', bedrag: '3215.00' },
        { datum: '2026-10-01', tegenpartij: 'Bakkerij De Korst', bedrag: '-12.50' },
      ],
      volgende: 'MjAyNi0xMC0wMSwy.q-_x',
    });
    await fixture.whenStable();
    fixture.detectChanges();

    [...pagina.querySelectorAll('button')].find((b) => b.textContent?.trim() === 'Toon meer')?.click();
    fixture.detectChanges();
    http.expectOne('/api/rekeningen/NL94SIMB0000000001/transacties?na=MjAyNi0xMC0wMSwy.q-_x').flush({
      transacties: [{ datum: '2026-10-01', tegenpartij: 'Woningstichting Thuis', bedrag: '-812.40' }],
      volgende: null,
    });
    await fixture.whenStable();
    fixture.detectChanges();

    const dagen = [...(pagina.querySelector('ul[aria-label="Transacties"]')?.children ?? [])];
    expect(dagen.map((dag) => dag.querySelector('h3')?.textContent)).toEqual([
      'Vrijdag 2 oktober 2026',
      'Donderdag 1 oktober 2026',
    ]);
    expect(dagen.map((dag) => dag.querySelectorAll('li').length)).toEqual([1, 2]);
    expect([...pagina.querySelectorAll('button')].some((b) => b.textContent?.trim() === 'Toon meer')).toBe(false);
  });
});
