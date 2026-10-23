import { datumZonderDag, tijdstipVoorWeergave } from './tijdstip';

/**
 * S4.1: "Datum transactie" en "Uitgevoerd op" in de details. Het tijdstip geldt in Europe/Amsterdam, wat de tijdzone van
 * de browser ook is; de e2e-browsers staan allemaal op Amsterdam en zouden een fout hier niet zien.
 */
describe('tijdstipVoorWeergave', () => {
  it.each([
    ['2026-10-01T06:02:00+02:00', '1 oktober 2026 om 06:02'],
    ['2026-10-01T04:02:00Z', '1 oktober 2026 om 06:02'],
    // Wintertijd: CET is UTC+1.
    ['2026-12-01T05:02:00Z', '1 december 2026 om 06:02'],
    // Vlak na middernacht in Amsterdam is het in UTC nog de dag ervoor.
    ['2026-09-30T22:30:00Z', '1 oktober 2026 om 00:30'],
  ])('%s wordt "%s"', (tijdstip, tekst) => {
    expect(tijdstipVoorWeergave(tijdstip)).toBe(tekst);
  });

  it('weigert iets anders dan een tijdstip', () => {
    expect(() => tijdstipVoorWeergave('gisteren')).toThrow('gisteren');
  });
});

describe('datumZonderDag', () => {
  it('"2026-10-01" wordt "1 oktober 2026", zonder verschuiving door de tijdzone', () => {
    expect(datumZonderDag('2026-10-01')).toBe('1 oktober 2026');
  });
});
