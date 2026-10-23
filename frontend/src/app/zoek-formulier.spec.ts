import { type ComponentFixture, TestBed } from '@angular/core/testing';
import { ZoekFormulier, type Zoekopdracht } from './zoek-formulier';

/** S5.1: het zoekformulier boven de transacties; de API doet de controle en het zoeken. */
describe('ZoekFormulier', () => {
  let fixture: ComponentFixture<ZoekFormulier>;
  let pagina: HTMLElement;
  let gezocht: Zoekopdracht[];
  let gewist: number;

  beforeEach(() => {
    fixture = TestBed.createComponent(ZoekFormulier);
    gezocht = [];
    gewist = 0;
    fixture.componentInstance.zoek.subscribe((opdracht) => gezocht.push(opdracht));
    fixture.componentInstance.wis.subscribe(() => gewist++);
    fixture.detectChanges();
    pagina = fixture.nativeElement as HTMLElement;
  });

  function knop(tekst: string): HTMLButtonElement | undefined {
    return [...pagina.querySelectorAll('button')].find((b) => b.textContent?.trim() === tekst);
  }

  function formulier(): HTMLFormElement | null {
    return pagina.querySelector('form[aria-label="Zoeken in transacties"]');
  }

  function veld(label: string): HTMLInputElement & HTMLSelectElement {
    const element = [...pagina.querySelectorAll('label')].find((l) => l.textContent?.trim() === label);
    return pagina.querySelector(`#${element?.htmlFor}`) as HTMLInputElement & HTMLSelectElement;
  }

  function radio(label: string): HTMLInputElement {
    const element = [...pagina.querySelectorAll('label')].find((l) => l.textContent?.trim() === label);
    return element?.querySelector('input[type="radio"]') as HTMLInputElement;
  }

  function vulIn(label: string, waarde: string): void {
    const element = veld(label);
    element.value = waarde;
    element.dispatchEvent(new Event(element.tagName === 'SELECT' ? 'change' : 'input'));
    // Zoals de browser: na elke invoer een ronde change detection.
    fixture.detectChanges();
  }

  function open(): void {
    knop('Zoeken in transacties')?.click();
    fixture.detectChanges();
  }

  it('opent met "Zoeken in transacties" en sluit met "Verberg zoeken"', () => {
    expect(formulier()).toBeNull();
    open();
    expect(formulier()).not.toBeNull();
    expect(knop('Zoeken in transacties')).toBeUndefined();
    knop('Verberg zoeken')?.click();
    fixture.detectChanges();
    expect(formulier()).toBeNull();
  });

  it('heeft de velden uit het FO, met "Naam of IBAN" omdat bedrag en omschrijving nog niet in de tekst meedoen', () => {
    open();
    expect(veld('Naam of IBAN')).not.toBeNull();
    expect(veld('Bedrag van (€)')).not.toBeNull();
    expect(veld('Bedrag t/m (€)')).not.toBeNull();
    expect([...veld('Transactietype').options].map((o) => [o.value, o.textContent?.trim()])).toEqual([
      ['', 'Alle transactietypes'],
      ['ONLINE_BANKIEREN', 'Online bankieren'],
      ['IDEAL_WERO', 'iDEAL | Wero'],
      ['BETAALAUTOMAAT', 'Betaalautomaat'],
      ['INCASSO', 'Incasso'],
      ['GELDAUTOMAAT', 'Geldautomaat'],
      ['OVERSCHRIJVING', 'Overschrijving'],
      ['VERZAMELBETALING', 'Verzamelbetaling'],
    ]);
    expect(radio('Alle transacties').checked).toBe(true);
    expect(radio('Uitgaande transacties').checked).toBe(false);
    expect(radio('Inkomende transacties').checked).toBe(false);
  });

  it('"Zoeken" meldt alleen de ingevulde velden, letterlijk', () => {
    open();
    vulIn('Naam of IBAN', 'albert');
    vulIn('Bedrag van (€)', '27,95');
    vulIn('Transactietype', 'INCASSO');
    radio('Inkomende transacties').click();
    knop('Zoeken')?.click();
    expect(gezocht).toEqual([{ tekst: 'albert', van: '27,95', type: 'INCASSO', richting: 'inkomend' }]);
  });

  it('"Wissen" maakt alle velden leeg, kiest "Alle transacties" en meldt het', () => {
    open();
    vulIn('Naam of IBAN', 'albert');
    vulIn('Bedrag t/m (€)', '100');
    radio('Uitgaande transacties').click();
    fixture.detectChanges();
    knop('Wissen')?.click();
    fixture.detectChanges();
    expect(veld('Naam of IBAN').value).toBe('');
    expect(veld('Bedrag t/m (€)').value).toBe('');
    expect(veld('Transactietype').value).toBe('');
    expect(radio('Alle transacties').checked).toBe(true);
    expect(gewist).toBe(1);
  });

  it('toont een melding van de API in het formulier', () => {
    fixture.componentRef.setInput('melding', 'Bedrag van mag niet groter zijn dan Bedrag t/m');
    open();
    expect(formulier()?.querySelector('[role="alert"]')?.textContent?.trim()).toBe(
      'Bedrag van mag niet groter zijn dan Bedrag t/m',
    );
  });
});
