# 0049. De acceptance stage wacht niet op de backend-tests

Datum: 2026-11-12
Status: Geaccepteerd

## Context
De jobs `acceptance` en `installatie` wachtten op alle jobs van de commit stage behalve PIT (ADR 0032): `backend`, `frontend`, `infrastructuur` en `images`. In run 43221071639 (10:25) waren die zo laat klaar:

| Job | Klaar na |
| --- | --- |
| infrastructuur (chart- en scripttests, actionlint) | 0:19 |
| frontend | 1:03 |
| images | 1:40 |
| backend (`mvnw verify`: unit, slice, ArchUnit, Testcontainers) | 2:50 |

De acceptance stage begon daardoor pas na 2:53. Hij heeft de images nodig, en een geldige chart en geldige scripts. Op de JUnit-tests van de backend hoeft hij niet te wachten. Toch wachtte hij daarop 1:10, en in eerdere runs 1:00 tot 1:30: backend duurde 2:31 tot 3:07, images 1:05 tot 1:40. Sinds ADR 0047 en 0048 bepaalt de acceptance stage de duur van de pipeline, dus die wachttijd telt helemaal mee.

Opties:
1. **Zo laten.** De acceptance stage test alleen een commit waarvan alle snelle tests groen zijn.
2. **Alleen wachten op `images`.** Dat is het vroegste moment. Een kapotte chart of een rode frontend merk je dan pas bij de installatie of in de UI-scenario's.
3. **Wachten op `images`, `frontend` en `infrastructuur`.** Beide zijn eerder klaar dan `images`, dus dat kost geen tijd. Een kapotte chart, script of frontend houdt de acceptance stage nog steeds tegen.
4. **Het cluster maken terwijl de images worden gebouwd.** Dat levert ongeveer 2:30 op. GitHub Actions kan alleen niet halverwege een job op een andere job wachten. De job zou moeten pollen tot het artifact met de images er is, en dat is foutgevoelig.

## Beslissing
We kiezen optie 3. In `.github/workflows/ci.yml` hebben `acceptance` en `installatie` `needs: [frontend, infrastructuur, images]`. `backend` blijft een verplichte job: is hij rood, dan is de run rood.

## Gevolgen
- **De pipeline duurt naar verwachting ongeveer 1:10 korter,** zolang `backend` langer duurt dan `images`.
- **Gemeten in CI** (run 43242264556, commit 014a646, vergeleken met run 43221071639):

  | | Run 43221071639 | Run 43242264556 |
  | --- | --- | --- |
  | `images` klaar na | 1:40 | 1:22 |
  | `backend` klaar na | 2:50 | 3:27 |
  | `acceptance` en de shards gestart na | 2:53 | 1:25 tot 1:31 |
  | Installatiescenario's 3/3 (de langzaamste job) | 7:27 | 8:32 |
  | **Pipeline** | **10:25** | **9:58** |

  De acceptance stage en de shards startten 3 tot 9 seconden nadat `images` klaar was; het doel was 15 seconden. Ze startten 1:28 eerder dan in de vorige run. De pipeline is maar 27 seconden korter, omdat shard 3 in deze run 1:05 langer duurde, met dezelfde scenario's. Gecorrigeerd voor die trage runner is de winst ongeveer 1:30, ruim boven het doel van 45 seconden. `backend` duurde 3:27, langer dan in alle eerdere runs; daar wacht de acceptance stage nu niet meer op.

  De run was rood door één UI-scenario in `zoeken.spec.ts` ("Zoekformulier openen en wissen", chromium). Volgens de momentopname van de pagina toonde de applicatie de goede zes transacties; de driver las de lijst terwijl die nog werd opgebouwd. Dat staat los van deze beslissing.
- **Bij een rode unittest draait de acceptance stage toch.** De pipeline is dan rood, maar de acceptance stage heeft runnertijd gekost. Voor de ontwikkelaar kost het geen tijd. Dat is dezelfde afweging als bij PIT (ADR 0032).
- **Twee rode jobs voor één oorzaak.** Een fout die een unittest vindt, kan ook een acceptatietest laten falen. Begin dan bij `backend`: die geeft de preciezere melding.
- **Het model van Farley wordt losser.** Daarin test de acceptance stage alleen wat de commit stage heeft goedgekeurd. De images zijn nog steeds de artefacten van de commit stage, één keer gebouwd. Alleen het oordeel van `mvnw verify` komt nu tegelijk met de acceptance stage binnen, in plaats van ervoor.
- **De images worden gebouwd zonder dat de tests gedraaid zijn.** Dat was al zo: `build-images.sh` draait Jib met `-DskipTests`. Hier verandert niets aan.

## Bewaking
- **actionlint** in de commit stage (`scripts/iac-check.sh`) controleert de workflow.
- **De eerste push:** het starttijdstip van `acceptance` en de shards, naast het eindtijdstip van `images` en `backend`, en de totale duur naast die van run 43221071639. Het resultaat komt in deze ADR, onder Gevolgen.
- **Dat een rode `backend` de run rood maakt,** volgt uit hoe GitHub Actions de uitkomst van een run bepaalt: elke job telt mee. Dat wordt niet met opzet nagespeeld, net als bij ADR 0032.
