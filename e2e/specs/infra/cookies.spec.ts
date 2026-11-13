import { test } from '../../dsl/fixtures';

/**
 * De cookies van de bank, zoals ze door de echte ingress komen (doc/werkwijze-mse.md, hoofdstuk 7, CFR OWASP Top 10;
 * ADR 0008, 0016 en 0050). Scenario 1 en 2 lezen de headers Set-Cookie van elk antwoord van de bank tijdens het
 * inloggen; na het inloggen krijgt de sessie een nieuw id, en dus een nieuw cookie. Scenario 3 laat in een echte browser
 * zien wat de vlag HttpOnly doet: een script op de pagina ziet het sessiecookie niet.
 */
test('Het sessiecookie is alleen via https te gebruiken en niet leesbaar voor JavaScript', async ({ omgeving, bank }) => {
  const jan = bank.klant('Jan');
  await omgeving.gegeven('BankSim-MSE is geïnstalleerd', () => omgeving.installeerAlsDatNodigIs());
  await omgeving.en('Jan is een klant van de bank', () => jan.heeftBetaalrekening());
  await omgeving.als('Jan inlogt via https://bank-mse.localtest.me', () => jan.logtInEnBewaartCookies());
  await omgeving.dan('heeft elk sessiecookie JSESSIONID dat de bank tijdens het inloggen zet, de vlaggen Secure, HttpOnly en SameSite=Lax', () =>
    jan.verwachtCookiesBijInloggen('JSESSIONID', { secure: true, httpOnly: true, sameSite: 'Lax' }),
  );
});

test('Het CSRF-cookie is alleen via https te gebruiken, maar wel leesbaar voor de app', async ({ omgeving, bank }) => {
  const jan = bank.klant('Jan');
  await omgeving.gegeven('BankSim-MSE is geïnstalleerd', () => omgeving.installeerAlsDatNodigIs());
  await omgeving.en('Jan is een klant van de bank', () => jan.heeftBetaalrekening());
  await omgeving.als('Jan inlogt via https://bank-mse.localtest.me', () => jan.logtInEnBewaartCookies());
  await omgeving.dan('heeft elk cookie XSRF-TOKEN dat de bank tijdens het inloggen zet, de vlaggen Secure en SameSite=Lax, en geen HttpOnly', () =>
    jan.verwachtCookiesBijInloggen('XSRF-TOKEN', { secure: true, httpOnly: false, sameSite: 'Lax' }),
  );
});

test('JavaScript op de pagina van de bank kan het sessiecookie niet lezen', async ({ omgeving, bank }) => {
  const jan = bank.klant('Jan');
  await omgeving.gegeven('BankSim-MSE is geïnstalleerd', () => omgeving.installeerAlsDatNodigIs());
  await omgeving.en('Jan is een klant van de bank', () => jan.heeftBetaalrekening());
  await omgeving.als('Jan in de browser inlogt via https://bank-mse.localtest.me', () => jan.logtInInDeBrowser());
  await omgeving.dan('ziet JavaScript op de pagina het cookie XSRF-TOKEN', () => jan.verwachtCookieVoorJavaScript('XSRF-TOKEN'));
  await omgeving.maar('niet het sessiecookie JSESSIONID', () => jan.verwachtCookieNietVoorJavaScript('JSESSIONID'));
});
