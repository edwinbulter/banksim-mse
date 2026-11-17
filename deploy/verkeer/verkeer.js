// ADR 0055: doorlopend verkeer op BankSim-MSE, zoals klanten het maken. Elke iteratie is een klantbezoek van een
// verkeersklant: inloggen via het formulier van Keycloak, het overzicht en de transacties bekijken, soms betalen aan een
// andere verkeersklant, en uitloggen. De klanten betalen alleen elkaar, dus het totaal van hun saldi blijft gelijk.
// deploy/scripts/verkeer.sh start dit script; SNELHEID (bezoeken per seconde), DUUR en VERKEER_WACHTWOORD komen van daar.
import http from 'k6/http';
import { check } from 'k6';

const BANK = 'https://bank-mse.localtest.me';
const KLANTEN = ['verkeer-1', 'verkeer-2', 'verkeer-3', 'verkeer-4', 'verkeer-5'];
/** 1 op de 20 bezoeken betaalt; 1 op de 10 betalingen is te groot en wordt geweigerd (ADR 0025). */
const KANS_OP_BETALEN = 0.05;
const KANS_OP_TE_GROOT = 0.1;

export const options = {
  // Het certificaat van de ingress is zelf ondertekend, net als bij curl -k in install.sh.
  insecureSkipTLSVerify: true,
  // Zonder url en name (standaard ook de URL, per redirect): die bevatten IBAN's, zoektermen en bij Keycloak een token of
  // sessiecode per bezoek. Dan groeien de reeksen met elk bezoek, tot de Collector de cijfers weigert. Ook error valt weg,
  // want die melding kan de URL noemen. De stap staat in het eigen label naam.
  systemTags: ['proto', 'status', 'method', 'scenario', 'expected_response', 'check', 'error_code', 'tls_version'],
  scenarios: {
    klantbezoeken: {
      // Een vaste snelheid: wordt de bank trager, dan blijft de load gelijk en zie je de vertraging.
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.SNELHEID || 2),
      timeUnit: '1s',
      duration: __ENV.DUUR || '10m',
      preAllocatedVUs: 10,
      maxVUs: 100,
    },
  },
  thresholds: {
    checks: ['rate>0.99'],
    http_req_failed: ['rate<0.01'],
  },
};

// Een weigering (422) is een antwoord van de bank, geen fout van het verkeer.
http.setResponseCallback(http.expectedStatuses({ min: 200, max: 399 }, 422));

export default function () {
  const nummer = Math.floor(Math.random() * KLANTEN.length);
  // Elk bezoek begint zonder cookies, zoals een nieuwe browser.
  http.cookieJar().clear(BANK);
  http.cookieJar().clear('https://auth-mse.localtest.me');
  if (!logIn(KLANTEN[nummer])) return;

  const overzicht = http.get(`${BANK}/api/rekeningen`, { tags: { naam: 'overzicht' } });
  if (!check(overzicht, { 'overzicht 200': (r) => r.status === 200 })) return;
  const rekening = overzicht.json().find((r) => r.soort === 'betaal');
  const transacties = http.get(`${BANK}/api/rekeningen/${rekening.iban}/transacties`, { tags: { naam: 'transacties' } });
  check(transacties, { 'transacties 200': (r) => r.status === 200 });

  if (Math.random() < KANS_OP_BETALEN) betaal(rekening, (nummer + 1 + Math.floor(Math.random() * (KLANTEN.length - 1))) % KLANTEN.length);
  logUit();
}

function logIn(gebruikersnaam) {
  const pagina = http.get(`${BANK}/oauth2/authorization/keycloak`, { tags: { naam: 'inlogpagina' } });
  const action = /<form[^>]*id="kc-form-login"[^>]*action="([^"]+)"/.exec(pagina.body || '')?.[1];
  if (!check(pagina, { inlogformulier: () => !!action })) return false;
  const terug = http.post(action.replaceAll('&amp;', '&'), { username: gebruikersnaam, password: __ENV.VERKEER_WACHTWOORD },
    { tags: { naam: 'inloggen' } });
  return check(terug, { 'ingelogd, terug op BankSim': (r) => r.url.replace(/\/$/, '') === BANK });
}

/** Aan een andere verkeersklant, met zijn naam uit de contactenlijst; soms meer dan het saldo. */
function betaal(rekening, naar) {
  const contact = http.get(`${BANK}/api/contacten?q=${encodeURIComponent(`Verkeer ${naar + 1}`)}`, { tags: { naam: 'contacten' } });
  const ontvanger = contact.json()[0];
  if (!check(contact, { 'contact gevonden': () => !!ontvanger })) return;
  const teGroot = Math.random() < KANS_OP_TE_GROOT;
  const bedrag = teGroot ? (Number(rekening.saldo) + 1000).toFixed(2) : (1 + Math.floor(Math.random() * 2500) / 100).toFixed(2);
  const antwoord = http.post(`${BANK}/api/betalingen`,
    JSON.stringify({ van: rekening.iban, naar: ontvanger.iban, naam: ontvanger.naam, bedrag }),
    { headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': csrfToken(), 'Idempotency-Key': crypto.randomUUID() }, tags: { naam: 'betalen' } });
  check(antwoord, { 'betaling geboekt of geweigerd zoals verwacht': (r) => r.status === (teGroot ? 422 : 201) });
}

/** Zoals de app: de BFF antwoordt 202 met de uitlog-URL van Keycloak (ADR 0021). */
function logUit() {
  const antwoord = http.post(`${BANK}/logout`, null, { headers: { 'X-XSRF-TOKEN': csrfToken() }, redirects: 0, tags: { naam: 'uitloggen' } });
  if (check(antwoord, { 'uitgelogd 202': (r) => r.status === 202 })) {
    http.get(antwoord.headers.Location, { tags: { naam: 'uitloggen bij Keycloak' } });
  }
}

function csrfToken() {
  return http.cookieJar().cookiesForURL(BANK)['XSRF-TOKEN']?.[0] ?? '';
}
