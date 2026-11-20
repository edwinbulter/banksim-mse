# 0056. De systeempakketten van Playwright komen in CI uit een cache

Datum: 2026-11-20
Status: Geaccepteerd

## Context
De browsers van Playwright staan in CI al in een cache (`~/.cache/ms-playwright`). De systeempakketten die ze nodig hebben niet: elke job draait `playwright install-deps`, en dat haalt ze met `apt-get` bij een Ubuntu-mirror. Voor de acceptatiejob zijn dat ongeveer 180 pakketten (chromium, firefox en webkit), voor de installatiejobs ongeveer 30 (alleen chromium).

Op 18 en 20 november liep de stap "Playwright-browsers" van de acceptatiejob drie keer tegen zijn time-out van 15 minuten, en draaide er geen scenario (backlog fase 7 punt 11). Wat de logs laten zien:
- Run 44296631242 (18 november): mirror `us-east-2.ec2.archive.ubuntu.com`, 141 pakketten in 15 minuten.
- Run 44776053202 (20 november): mirror `azure.archive.ubuntu.com`, de mirror van de laatste groene run. De acceptatiejob kreeg in 15 minuten 56 pakketten binnen. In de installatiejob overig-1 duurde het downloaden 9 min 14 s (32,1 MB, 57,9 kB/s), in de drie andere installatiejobs onder 1 minuut.
- `apt-get update` was in beide jobs van 20 november snel: 13 MB in 1 à 2 s. Alleen het downloaden van de pakketten was traag.

Een andere mirror vastzetten hielp dus niet (a09ca7b, teruggedraaid). Waarom apt op sommige runners zo traag is, weten we niet.

Opties:
1. **De `.deb`-pakketten in een cache**, net als de browsers. `install-deps` blijft; apt vindt de pakketten in de cache en downloadt alleen wat ontbreekt of nieuwer is.
2. **Het officiële image van Playwright** (`mcr.microsoft.com/playwright:v1.63.0-noble`, op digest vastgezet), met de pakketten en de browsers er al in. Als `container:` van de job werkt dat niet: kind en de ingress luisteren op `127.0.0.1` van de runner, en de action `kind-banksim-mse` zou dan in de container draaien. Het moet dus een `docker run --network host` worden voor alleen de teststap, met de workspace, de kubeconfig, `kubectl`, `helm` en `docker` erbij: de drivers roepen die drie aan.
3. **Niets aan de pakketten doen, alleen de time-out verhogen of een nieuwe poging doen.** Een trage mirror maakt de pipeline dan nog steeds een kwartier of langer trager, en het is niet bekend hoe traag hij kan worden.

## Beslissing
Optie 1: de systeempakketten van Playwright komen in CI uit een cache, per job-soort.
- Een bestand in `/etc/apt/apt.conf.d/` zet de map voor gedownloade pakketten (`Dir::Cache::Archives`) op een map van de runner-gebruiker, zodat `actions/cache` hem kan terugzetten en bewaren.
- `actions/cache/restore` vóór en `actions/cache/save` direct ná de stap met `install-deps`, beide op SHA vastgezet zoals de andere actions. Niet het gewone `actions/cache`: dat bewaart pas aan het eind van een geslaagde job, en een job die later faalt, vult de cache dan nooit.
- De sleutel bevat de browsers van de job en de Playwright-versie uit `e2e/package-lock.json`, niet de versie van het runner-image. In run 44807145564 draaiden runners met twee versies van het image (`20261116.376.1` en `20261116.133.1`) door elkaar; met de versie in de sleutel vond een job de cache van een vorige run dan vaak niet.

## Gevolgen
- Met een warme cache downloadt apt niets of bijna niets; alleen `apt-get update` gaat nog over het netwerk, en dat was ook in de trage runs snel.
- Geen nieuw image en geen nieuwe tool; de teststappen blijven zoals ze zijn.
- Een lege cache met een trage mirror faalt nog steeds. Dat gebeurt na een nieuwe Playwright-versie, en als GitHub de cache opruimt omdat hij zeven dagen niet gebruikt is. Een nieuwe poging vult de cache dan, zodra één job de pakketten binnen heeft.
- Een nieuwere versie van een pakket in `noble-updates` haalt apt alsnog bij de mirror, en die komt niet in de cache: een cache met een bestaande sleutel wordt niet overschreven. Hoe meer pakketten verouderen, hoe meer er weer over de mirror gaat; een nieuwe Playwright-versie geeft een nieuwe sleutel en dus een verse cache.
- De cache kost ruimte in de 10 GB van de repository: een paar honderd MB voor de acceptatiejob, minder voor de installatiejobs.
- Optie 2 hoeft geen apt meer, maar trekt elke run een image van ongeveer 2 GB (niet gemeten) en verandert hoe de tests draaien. Dat bewaren we voor als optie 1 niet genoeg blijkt.

## Bewaking
- actionlint in de commit stage (`make iac`).
- Meetbaar doel (backlog fase 7 punt 11): met een warme cache duurt de stap met `install-deps` hoogstens 1 minuut, en het log toont geen download van een Ubuntu-mirror. Gemeten in één run met een lege cache en de run daarna.
- Faalt de stap toch weer op apt met een warme cache, dan is deze beslissing niet genoeg en volgt optie 2 in een nieuwe ADR.
