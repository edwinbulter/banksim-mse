# 0040. De demo-data komt uit een Job met bank-seed, na elke installatie

Datum: 2026-11-02
Status: Geaccepteerd

## Context
Scenario "Na installatie kunnen 10 klanten en een beheerder inloggen" (S11.1, `e2e/specs/infra/huishoudens.spec.ts`). Na `install.sh` moeten de 10 klanten en een beheerder bestaan, zonder verdere handelingen.

Rood gezien, in project `infra`: `Secret bank-demo ophalen faalde: secrets "bank-demo" not found`. De demo-gebruikers en hun wachtwoorden bestaan nog niet.

Er zijn drie vragen die samen één beslissing vormen: hoe het aanmaken start, welke code het doet en waar de logins vandaan komen. `bank-seed` kan al klanten, beheerders en spaarrekeningen aanmaken, met de domeinregels, een eigen databasegebruiker en een eigen client in Keycloak (ADR 0003). Zijn image wordt al bij elke installatie geladen, maar er draait geen Deployment van.

Opties per vraag:

**Hoe het start**
1. **Een K8s Job in de chart, als Helm-hook na install en upgrade.** Dezelfde vorm als de K8s Job `keycloak-config` (ADR 0017). Faalt hij, dan faalt de installatie. Na succes verwijdert Helm hem, dus er blijft niets draaien.
2. **Een losse pod vanuit `install.sh`, met `kubectl run`.** Geen wijziging in de chart. Maar wie de chart zonder het script installeert, krijgt geen data, en de pod staat los van de Helm-release.

**Welke code het doet**
1. **`bank-seed` uitbreiden.** Geen nieuwe module, image of rechten. ADR 0003 ging er al van uit dat `bank-seed` dit zou worden.
2. **Een eigen module `bank-datagen`, zoals het origineel en de werkwijze.** Scheidt demo-data van testdata, maar er komt een tweede module, image en set rechten bij die grotendeels hetzelfde doen.

**Waar de logins vandaan komen**
1. **Via de Admin API van Keycloak.** `bank-seed` doet dat al, en maakt de login samen met de rekeninghouder aan.
2. **Uit de realm-JSON, als vaste gebruikers.** De K8s Job `keycloak-config` importeert ze dan. De rekeningen moeten nog steeds ergens anders vandaan komen, en logins en rekeningen raken zo uit elkaar.

## Beslissing
We kiezen een K8s Job in de chart, die `bank-seed` draait en de logins via de Admin API van Keycloak aanmaakt:
- **Commando.** `bank-seed demo` maakt de 10 klanten met een betaal- en een spaarrekening en de beheerder aan. De namen en gebruikersnamen komen uit het origineel.
- **Wanneer.** Een K8s Job `bank-demo` in de chart, als hook `post-install,post-upgrade`, ná `keycloak-config` (`helm.sh/hook-weight`), met `before-hook-creation,hook-succeeded` als delete-policy.
- **Wachtwoorden.** `install.sh` maakt één keer het Secret `bank-demo` aan, met `KLANT_WACHTWOORD` en `BEHEERDER_WACHTWOORD` (ADR 0009). De beheerder zoekt ze op met `kubectl`; ze staan niet in Git.
- **Herhalen.** Een gebruiker die al bestaat, slaat de opdracht over. Zo verandert een tweede installatie of upgrade niets, en wordt een afgebroken eerste poging afgemaakt.

Deze ADR gaat alleen over de gebruikers en rekeningen. De transacties (historie, einddatum, nooit rood) krijgen hun eigen scenario's en, als het nodig is, een eigen ADR.

## Gevolgen
- De demo-data is er na elke installatie, ook in CI en op de bestaande lokale installatie. Daar komen de klanten erbij naast de testdata; de scenario's werken met hun eigen aliassen.
- De spaarrekeningen krijgen voorlopig geen datum van openen. De maandafsluiting slaat ze daardoor over, en er is nog geen rente.
- `bank-seed` wordt ook een onderdeel van de installatie, niet alleen testgereedschap. ADR 0003 hield daar rekening mee; de rechten veranderen niet. `values.yaml` krijgt het image `bank-seed` erbij.
- De K8s Job draait na elke upgrade, en kost dan een paar seconden om te zien dat alles er al is.
- `bank-seed` blijft de naam, ook al noemt ADR 0003 `bank-datagen`. Hernoemen levert nu niets op.
- Een wijziging van het wachtwoord in Keycloak door iemand met de hand blijft staan: de K8s Job past bestaande gebruikers niet aan.

## Bewaking
- **Scenario:** "Na installatie kunnen 10 klanten en een beheerder inloggen".
- **Scenario "Opnieuw installeren verandert niets":** bewaakt dat de K8s Job niets achterlaat.
- **Unittests van `bank-seed`:** `demo` maakt alle gebruikers aan; een tweede keer maakt niets bij; een half afgemaakte eerste keer wordt aangevuld.
- **helm-unittest:** de K8s Job is een hook, ná `keycloak-config`, in namespace `banksim-mse`, zonder letterlijk wachtwoord.
- **bats:** `install.sh` maakt het Secret `bank-demo` één keer aan, en laat een bestaand Secret ongewijzigd.
