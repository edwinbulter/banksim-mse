/** Soort rekening, zoals de DSL en de API hem noemen. */
export type Soort = 'betaal' | 'spaar';

/** Een rekening zoals het overzicht hem toont; het saldo in domeinnotatie ("1842.17"). */
export interface RekeningInOverzicht {
  soort: Soort;
  iban: string;
  saldo: string;
}

/** Een transactie zoals de klant hem ziet; het bedrag met teken in domeinnotatie ("-812.40"). */
export interface Transactie {
  tegenpartij: string;
  bedrag: string;
}

/** De transacties van één dag ("2026-10-02"), in de volgorde waarin het systeem ze toont. */
export interface TransactiesOpDatum {
  datum: string;
  transacties: Transactie[];
}

/** De 7 transactietypen (FO); het scherm toont ze als "Incasso", "iDEAL | Wero" enzovoort. */
export type TransactieType =
  | 'ONLINE_BANKIEREN'
  | 'IDEAL_WERO'
  | 'BETAALAUTOMAAT'
  | 'INCASSO'
  | 'GELDAUTOMAAT'
  | 'OVERSCHRIJVING'
  | 'VERZAMELBETALING';

/** Een kant van de transactie: de naam en het IBAN, of null als er geen IBAN is (betaalautomaat). */
export interface Partij {
  naam: string;
  iban: string | null;
}

/**
 * De details van een uitgeklapte transactie (S4.1), in domeinnotatie: het tijdstip als "2026-10-01T06:02" en de
 * uitvoerdatum als "2026-10-01", allebei in Europe/Amsterdam.
 */
export interface TransactieDetails {
  transactietype: TransactieType;
  van: Partij;
  naar: Partij;
  datumTransactie: string;
  uitgevoerdOp: string;
  /** Alleen als ze er zijn (FO): bij een betaling uit S7.1. */
  omschrijving?: string;
  betalingskenmerk?: string;
}

/** Inkomend: bedrag > 0; uitgaand: bedrag < 0 (FO "Filteren op"). */
export type Richting = 'inkomend' | 'uitgaand';

/**
 * Een zoekopdracht zoals de klant hem invult (S5.1); lege velden doen niet mee, alle ingevulde moeten gelden. Bedragen
 * zijn de letterlijke invoer, ook met komma ("27,95"), en gelden voor de absolute waarde.
 */
export interface Zoekopdracht {
  tekst?: string;
  van?: string;
  tm?: string;
  type?: TransactieType;
  richting?: Richting;
}

/** Het zoekformulier zoals het op het scherm staat; type '' is "Alle transactietypes", richting 'alle' de standaard. */
export interface Zoekformulier {
  tekst: string;
  van: string;
  tm: string;
  type: string;
  richting: Richting | 'alle';
}

/** Een betaling zoals de klant hem invult (S7.1): het bedrag in domeinnotatie ("25.00"), de naam en het IBAN van de ontvanger. */
export interface Betaalopdracht {
  bedrag: string;
  naam: string;
  iban: string;
  /** Optioneel (FO): hoogstens 140 en 25 tekens. */
  omschrijving?: string;
  betalingskenmerk?: string;
}

/** De maximale lengtes van de velden op het betaalscherm (FO). */
export interface MaximaleLengtes {
  naamOntvanger: number;
  rekeningnummer: number;
  omschrijving: number;
  betalingskenmerk: number;
  extraOmschrijving: number;
}

/** Een mutatie op de spaarrekening (S8.1): de uitvoerdatum, het soort en het bedrag met teken in domeinnotatie. */
export interface Spaarmutatie {
  datum: string;
  type: 'Rente' | 'Inleg' | 'Opname';
  /** Zoals op het scherm: "Rente januari", "Rentecorrectie februari", "Inleg". */
  omschrijving: string;
  bedrag: string;
}

/** Een overschrijving tussen de eigen rekeningen (S9.1): van en naar zijn IBAN's, het bedrag in domeinnotatie. */
export interface Overschrijfopdracht {
  van: string;
  naar: string;
  bedrag: string;
}

/**
 * Of een verzoek dat iets verandert, is uitgevoerd of geweigerd: door de rechten (403) of door een regel van de bank
 * (400 of 422). De driver geeft de uitkomst terug; de DSL beweert erover (ADR 0031).
 */
export type Uitkomst = 'gelukt' | 'geweigerd';

/** S10.1: een regel op het beheerscherm; bedragen in domeinnotatie, zonder spaarrekening null. */
export interface RekeninghouderInBeheer {
  naam: string;
  betaalrekening: string;
  saldo: string;
  spaarrekening: string | null;
  spaarsaldo: string | null;
}

/** Wat de DSL van het systeem nodig heeft. De REST- en de UI-driver implementeren dit elk op hun eigen manier. */
export interface BankDriver {
  openStartpagina(): Promise<void>;
  /** De versie die BankSim toont of teruggeeft. */
  versie(): Promise<string>;
  /**
   * S10.1: als beheerder de betaalrekening van een klant openen, alleen-lezen; daarna leest transacties() de lijst.
   */
  openRekeningInBeheer(iban: string): Promise<void>;
  /**
   * S10.1: vanaf de geopende rekening van een klant betalen, als beheerder. Geweigerd: via de API een weigering, in de
   * browser geen knop "Betalen".
   */
  betaalInBeheer(): Promise<Uitkomst>;
  /** S10.2: als beheerder de simulatiedatum zetten op het beheerscherm; via REST is dat dezelfde PUT als zetSimulatiedatum. */
  zetSimulatiedatumOpScherm(datum: string): Promise<Uitkomst>;
  /** S10.2: "Terug naar vandaag" op het beheerscherm; via REST is dat dezelfde DELETE als wisSimulatiedatum. */
  wisSimulatiedatumOpScherm(): Promise<Uitkomst>;
  /** S10.2: de melding bij de laatste geweigerde simulatiedatum op het beheerscherm, anders undefined. */
  simulatiedatumMelding(): Promise<string | undefined>;
  /** S10.2: de datum waarop de bank staat ("2026-10-05"), zoals de beheerder hem ziet. */
  simulatiedatum(): Promise<string>;
  /** S10.1: als beheerder de spaarrekening van een klant openen, alleen-lezen; daarna leest spaarmutaties() de lijst. */
  openSpaarrekeningInBeheer(iban: string): Promise<void>;
  /**
   * S10.1: vanaf de geopende spaarrekening van een klant opnemen, als beheerder. Geweigerd: via de API een weigering,
   * in de browser geen knoppen "Inleggen" en "Opnemen".
   */
  neemOpInBeheer(opdracht: Overschrijfopdracht): Promise<Uitkomst>;
  /** Alleen in de UI-driver (@ui): welk scherm de app nu toont (S10.1, ADR 0037). */
  scherm(): Promise<'overzicht' | 'beheer'>;
  /** Inloggen via Keycloak en de BFF, zoals een gebruiker dat doet. */
  logIn(gebruikersnaam: string, wachtwoord: string): Promise<void>;
  /** Alleen in de UI-driver (@ui): inloggen zonder te verwachten dat het lukt (S6.1). */
  probeerInTeLoggen(gebruikersnaam: string, wachtwoord: string): Promise<void>;
  /** De melding op het inlogscherm, of undefined als BankSim al geladen is. */
  inlogfout(): Promise<string | undefined>;
  /** Uitloggen via "Uitloggen" (S6.2). */
  logUit(): Promise<void>;
  /** Of BankSim de rekeningen toont, of in plaats daarvan "Inloggen". */
  ingelogd(): Promise<boolean>;
  /** "Inloggen" kiezen: of Keycloak dan om gebruikersnaam en wachtwoord vraagt, in plaats van meteen terug te sturen. */
  inloggenVraagtWachtwoord(): Promise<boolean>;
  openOverzicht(): Promise<void>;
  rekeningenInOverzicht(): Promise<RekeningInOverzicht[]>;
  /** Vanuit het overzicht de rekening met dit IBAN openen. */
  openRekening(iban: string): Promise<void>;
  /** De rekening met dit IBAN openen via het adres, zonder het overzicht (S6.1). */
  openRekeningViaAdres(iban: string): Promise<void>;
  /** Of het systeem meldt dat de laatst geopende rekening niet bestaat (S6.1). */
  rekeningNietGevonden(): Promise<boolean>;
  /** De transacties van de geopende rekening die tot nu toe geladen zijn, per datum, in de volgorde van het systeem. */
  transacties(): Promise<TransactiesOpDatum[]>;
  /** Of er nog oudere transacties te laden zijn ("Toon meer"). */
  heeftMeer(): Promise<boolean>;
  /** De volgende portie oudere transacties laden. */
  toonMeer(): Promise<void>;
  /** De transactie met deze tegenpartij uitklappen (S4.1). */
  klapUit(tegenpartij: string): Promise<void>;
  /** De details van de transactie die het laatst is uitgeklapt. */
  details(): Promise<TransactieDetails>;
  /** Zoeken in de transacties van de geopende rekening; de lijst toont daarna wat gevonden is (S5.1). */
  zoek(opdracht: Zoekopdracht): Promise<void>;
  /**
   * Het probleemtype ("ongeldig-bedragbereik", "onbekend-contact") als de laatste zoekopdracht, betaling of
   * overschrijving geweigerd werd, anders undefined.
   */
  weigering(): Promise<string | undefined>;
  /** Alleen in de UI-driver (@ui): het zoekformulier openen, de tekst van de knop, wissen en de inhoud lezen. */
  openZoeken(): Promise<void>;
  zoekknop(): Promise<string>;
  wisZoeken(): Promise<void>;
  zoekformulier(): Promise<Zoekformulier>;
  /** Vanaf een geopende rekening terug naar het overzicht, via de link "Overzicht". */
  naarOverzicht(): Promise<void>;
  /** De terugknop van de browser; alleen in de UI-driver (@ui). */
  terugInBrowser(): Promise<void>;
  /** Of de app sinds de laatste terugInBrowser opnieuw is geladen, in plaats van binnen de pagina terug te gaan. */
  opnieuwGeladen(): Promise<boolean>;
  /** De pagina herladen; alleen in de UI-driver (@ui). */
  herlaad(): Promise<void>;
  /**
   * Vanaf de geopende rekening betalen en op het controlescherm bevestigen (S7.1). Gelukt: daarna staat de rekening weer
   * open. Geweigerd: weigering() geeft het probleemtype ("onbekend-contact").
   */
  betaal(opdracht: Betaalopdracht): Promise<Uitkomst>;
  /**
   * Betalen zoals betaal, maar de bevestiging gaat twee keer de deur uit: in de API twee keer hetzelfde verzoek, in de
   * browser een dubbelklik op "Bevestigen" (S7.1, CFR).
   */
  betaalDubbel(opdracht: Betaalopdracht): Promise<Uitkomst>;
  /** Alleen in de UI-driver (@ui): vanaf de geopende rekening het betaalscherm openen. */
  openBetalen(): Promise<void>;
  /** Alleen in de UI-driver (@ui): hoeveel tekens er in elk veld van het betaalscherm passen. */
  maximaleLengtes(): Promise<MaximaleLengtes>;
  /** Alleen in de UI-driver (@ui): op het betaalscherm `tekst` typen en de suggestie met dit IBAN kiezen. */
  kiesSuggestie(tekst: string, iban: string): Promise<void>;
  /** Alleen in de UI-driver (@ui): de naam en het IBAN van de ontvanger zoals ze in het formulier staan. */
  ontvangerInFormulier(): Promise<{ naam: string; iban: string }>;
  /** Vanuit het overzicht de spaarrekening met dit IBAN openen (S8.1). */
  openSpaarrekening(iban: string): Promise<void>;
  /** De mutaties van de geopende spaarrekening, nieuw naar oud. */
  spaarmutaties(): Promise<Spaarmutatie[]>;
  /**
   * De datumregels ("2026-01-31") en jaartalregels ("2025") van de geopende spaarrekening, van boven naar beneden.
   */
  spaarkoppen(): Promise<string[]>;
  /**
   * Vanaf de geopende spaarrekening geld inleggen (S9.1). Gelukt: daarna staat de spaarrekening weer open. Geweigerd:
   * weigering() geeft het probleemtype.
   */
  legIn(opdracht: Overschrijfopdracht): Promise<Uitkomst>;
  /** Vanaf de geopende spaarrekening geld opnemen (S9.1), net als legIn ("saldo-ontoereikend"). */
  neemOp(opdracht: Overschrijfopdracht): Promise<Uitkomst>;
  /** De lopende rente van de geopende spaarrekening, in domeinnotatie ("12.36"). */
  lopendeRente(): Promise<string>;
  /** S10.1, als beheerder: zoeken op naam of rekeningnummer; geeft de gevonden rekeninghouders, hoogstens 50. */
  zoekRekeninghouders(tekst: string): Promise<RekeninghouderInBeheer[]>;
  /**
   * S10.1: het beheerscherm openen. Gelukt: de rekeninghouders zijn te zien. Geweigerd: via de API een 403, in de
   * browser het eigen overzicht in plaats van het beheerscherm.
   */
  openBeheer(): Promise<Uitkomst>;
  /** De simulatiedatum ("2026-10-05") zetten met de eigen sessie; tot S10.2 via de API, ook in de UI-driver. */
  zetSimulatiedatum(datum: string): Promise<Uitkomst>;
  /** De simulatiedatum wissen: de bank draait weer op vandaag. */
  wisSimulatiedatum(): Promise<Uitkomst>;
}
