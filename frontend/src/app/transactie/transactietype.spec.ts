import { typeVoorWeergave } from './transactietype';

/** S4.1: de 7 transactietypen uit het FO, zoals de details ze tonen. */
describe('typeVoorWeergave', () => {
  it.each([
    ['ONLINE_BANKIEREN', 'Online bankieren'],
    ['IDEAL_WERO', 'iDEAL | Wero'],
    ['BETAALAUTOMAAT', 'Betaalautomaat'],
    ['INCASSO', 'Incasso'],
    ['GELDAUTOMAAT', 'Geldautomaat'],
    ['OVERSCHRIJVING', 'Overschrijving'],
    ['VERZAMELBETALING', 'Verzamelbetaling'],
  ])('%s wordt "%s"', (type, label) => {
    expect(typeVoorWeergave(type)).toBe(label);
  });

  it('weigert een type dat het FO niet kent', () => {
    expect(() => typeVoorWeergave('CONTANT')).toThrow('CONTANT');
  });
});
