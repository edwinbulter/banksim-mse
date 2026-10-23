# 0017. Realm-wijzigingen bereiken een bestaande installatie via keycloak-config-cli

Datum: 2026-10-22
Status: Geaccepteerd

## Context
Slice 3 voegt de rol `beheerder` en de scope `roles` voor `bank-bff` toe aan de realm (ADR 0015). Keycloak start met `--import-realm`, en dat slaat een realm over die al bestaat. Een verse installatie krijgt de wijziging dus wel, maar je lokale installatie, de upgrade in CI en elke echte upgrade niet. Dat geldt voor elke realm-wijziging die nog komt (slice 6 en 10). Opnieuw installeren met een lege PVC is geen oplossing, want dan gaat de data verloren (ADR 0009, `upgrade.spec.ts`).

Opties:
1. **keycloak-config-cli** (adorsys) als Helm-Job na elke installatie en upgrade. Hij past de realm-JSON toe op de bestaande realm: hij voegt toe, past aan, en verwijdert alleen wat hij zelf eerder aanmaakte.
2. **Een Job met `kcadm.sh`** uit het Keycloak-image. Er komt geen nieuwe tool bij, maar elke wijziging moet dan dubbel worden bijgehouden: in de JSON én als idempotent script.
3. **Niets doen.** Een upgrade mist dan de rol, en de beheerder kan na een upgrade de datum niet zetten.

## Beslissing
- **Tool.** keycloak-config-cli draait als Helm-hook-Job (`post-install`, `post-upgrade`). Het image is vastgepind op digest (ADR 0006).
- **Bron.** De realm-JSON in `deploy/banksim-mse/files/` blijft de enige bron.
- **Inloggen.** De Job logt in met de bootstrap-admin van Keycloak, uit het Secret `keycloak`.
- **Eén pad.** `--import-realm` vervalt; ook de eerste installatie loopt via de Job. De placeholders voor geheimen in de JSON krijgen daarom de vorm van keycloak-config-cli (`$(env:…)`) in plaats van die van Keycloak (`${…}`).
- **Versie.** Er is nog geen build voor Keycloak 26.8. We nemen `adorsys/keycloak-config-cli:6.5.1-26.5.5`, de nieuwste, op digest. Een proef in losse Docker-containers op 2026-10-22 werkte tegen Keycloak 26.8.0. Daarin kreeg een bestaande realm zonder de rol `beheerder` de rol erbij, en `bank-bff` de scope `roles`. Het clientgeheim kwam uit een omgevingsvariabele (`$(env:…)`), en een bestaande gebruiker bleef staan. Zodra er een build voor 26.8 is, stappen we over.

## Gevolgen
- Een realm-wijziging is voortaan alleen een wijziging aan de JSON. Installatie en upgrade passen hem toe.
- Er komt een image van een derde partij bij, met een versie die op Keycloak moet aansluiten. Bij een Keycloak-upgrade moet ook dit image mee.
- `install.sh` wacht op de Job. Faalt de Job, dan faalt de installatie.
- Wat met de hand in de Keycloak-console is veranderd, blijft staan, tenzij het in de JSON staat. Dan wint de JSON.

## Bewaking
- helm-unittest: de Job bestaat als hook, met het image op digest en het wachtwoord uit het Secret.
- De scenario's met een beheerder falen als de rol of het recht van de seed ontbreekt.
- Een realm van vóór een wijziging komt in CI niet voor, want `upgrade.spec.ts` installeert twee keer dezelfde chart. Het echte geval is daarom lokaal gecontroleerd, op 2026-10-22. De installatie van vóór slice 3 kreeg via de Job de rol `beheerder`, en daarna ook het extra recht `view-realm` voor de seed (ADR 0015). Pas toen werkten de scenario's. Bij een volgende realm-wijziging: draai eerst de scenario's op de bestaande lokale installatie, vóór een `make uninstall`.
