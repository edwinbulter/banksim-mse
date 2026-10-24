import { test } from '../dsl/fixtures';

test('De startpagina toont de versie', { tag: '@rook' }, async ({ bank }) => {
  const bezoeker = bank.bezoeker();
  await bank.als('een bezoeker BankSim opent', () => bezoeker.opentBankSim());
  await bank.dan('wordt de versie van BankSim getoond', () => bezoeker.verwachtVersie());
});
