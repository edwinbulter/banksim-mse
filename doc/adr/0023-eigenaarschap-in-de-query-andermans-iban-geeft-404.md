# 0023. Eigenaarschap zit in de query; andermans IBAN geeft 404

Datum: 2026-10-24
Status: Geaccepteerd

## Context
Een klant mag alleen zijn eigen rekeningen en transacties zien (S6.1). Scenario "Andermans rekening bestaat niet" (`e2e/specs/toegang.spec.ts`): Jan opent via het adres `/rekeningen/<iban>` de rekening van Sanne, en ziet "Rekening is niet gevonden.".

Dit is al zo gebouwd sinds slice 1 en 2, maar het staat nog in geen ADR. ADR 0011 en 0022 noemen het alleen terloops. De backlog (werkwijze §6) vraagt om deze beslissing bij slice 6.

ADR 0022 legt de rol per endpoint vast. Die zegt alleen dát een klant het endpoint mag aanroepen, en niet voor welke rekening. Eigenaarschap hangt af van de data, niet van de rol.

Opties:
1. **Eigenaarschap in de query.** Elke query die gegevens van een klant leest, krijgt de gebruikersnaam uit het token (`IngelogdeGebruiker`) en filtert erop (`WHERE h.gebruikersnaam = :gebruikersnaam`). Andermans IBAN levert dus niets op, en de API antwoordt 404, net als bij een IBAN die niet bestaat.
2. **Een aparte controle vóór de query**, die 403 geeft voor andermans rekening. Afgewezen:
   - een 403 verraadt dat de IBAN bestaat en van een ander is;
   - de controle is een losse stap die je bij een nieuwe query kunt vergeten, en dan leest de query alles.
3. **Achteraf filteren**, bijvoorbeeld met `@PostAuthorize` op het resultaat. Afgewezen:
   - de gegevens zijn dan al gelezen;
   - bij paginering (ADR 0011) klopt het aantal per pagina niet meer.

## Beslissing
We kiezen optie 1:
- een repository die gegevens van een klant leest, krijgt de gebruikersnaam als parameter en filtert erop in de SQL;
- de gebruikersnaam komt altijd uit het gevalideerde token (`IngelogdeGebruiker`, ADR 0008), nooit uit het verzoek;
- andermans IBAN, een onbekende IBAN en een ongeldige IBAN geven alle drie hetzelfde antwoord: 404;
- de app toont dan "Rekening is niet gevonden.".

## Gevolgen
- Een klant kan niet afleiden of een IBAN bestaat.
- Een nieuwe query voor klantgegevens moet de gebruikersnaam meenemen. Dat dwingt geen ArchUnit-regel af: de repositorytests en een 404-test per endpoint bewaken het.
- De beheerder (slice 10) leest straks gegevens van alle klanten. Dat gaat via aparte queries op endpoints met `hasRole('beheerder')` (ADR 0022), niet door de filter weg te laten in de queries van de klant.
- Betalen naar andermans IBAN (slice 7) schrijft naar die rekening, maar leest hem niet voor de klant. Het grootboek valt buiten deze regel. De klant ziet van de tegenpartij alleen wat in de eigen transactie staat.

## Bewaking
- **Repositorytests** (Testcontainers):
  - `JdbcRekeningRepositoryTests`: alleen de rekeningen van de gebruiker;
  - `JdbcTransactieRepositoryTests.eenRekeningVanEenAnderGeeftNiets`.
- **Controllertests:**
  - `TransactieControllerTests.eenRekeningVanEenAnderGeeft404` en `eenOngeldigIbanGeeft404`;
  - `RekeningControllerTests.geeftGeenRekeningenVanEenAndereGebruiker`.
- **Het scenario "Andermans rekening bestaat niet"**, in rest en in chromium.
- **Een nieuw endpoint met klantgegevens** krijgt in zijn slice een 404-test op andermans data. Dat staat in werkwijze §7 onder de fitness functions van slice 6.
