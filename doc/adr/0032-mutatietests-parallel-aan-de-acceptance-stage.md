# 0032. Mutatietests parallel aan de acceptance stage

Datum: 2026-10-31
Status: Geaccepteerd

## Context
ADR 0018 zette PIT als eigen job in de commit stage. De twee jobs van de acceptance stage wachten op alle jobs van de commit stage, dus ook op PIT. In run 41234785344 duurde PIT 8:40, terwijl `backend` (1:24), `frontend` (1:13), `infrastructuur` (0:15) en `images` (0:49) allang klaar waren. De acceptatie (ongeveer 6:30) begon pas daarna. De pipeline duurde zo ongeveer 15 minuten, en PIT bepaalde ruim de helft.

De acceptance stage heeft PIT niet nodig. Ze installeert de images en draait de scenario's; daarvoor zijn de images en groene unittests genoeg. Farley wil een commit stage van hooguit een paar minuten, die snel zegt of een wijziging goed genoeg is voor de volgende stap. PIT is daarvoor te traag.

Opties:
1. **Zo laten.** De acceptatie start alleen na een groene PIT-run; geen rekentijd verspild aan een commit die PIT afkeurt. De pipeline blijft ongeveer 15 minuten.
2. **PIT parallel aan de acceptance stage.** De acceptatie wacht alleen op `backend`, `frontend`, `infrastructuur` en `images`. PIT blijft verplicht: is het rood, dan is de pipeline rood. Doorlooptijd ongeveer max(8:40, 1:24 + 6:30), zo'n 9 minuten.
3. **PIT sneller maken** (incrementele analyse met de geschiedenis van PIT, of minder mutators). Dat vraagt een cache tussen runs en meer configuratie, en de winst is onzeker.

## Beslissing
We kiezen optie 2. De job heet nu "Mutatietests (PIT)", niet meer "Commit stage – mutatietests", omdat hij de acceptance stage niet meer tegenhoudt. Wat ADR 0018 verder vastlegt, blijft gelden: de drempel van 95% per module en de uitsluitingen.

## Gevolgen
- Een commit die PIT afkeurt, kan toch door de acceptance stage gaan. De pipeline is dan rood, maar de acceptatie heeft rekentijd op de runner gekost. Voor de ontwikkelaar kost het geen tijd.
- De pipeline duurt ongeveer 9 in plaats van 15 minuten. Dat moet de eerste run na deze wijziging laten zien.
- "Is de pipeline groen, ook de mutatietests?" uit werkwijze §10.1 blijft dezelfde vraag; alleen de naam van de job verandert.

## Bewaking
- **actionlint** in `scripts/iac-check.sh` controleert dat de workflow geldig is.
- **De pipeline zelf:** de eerste run laat zien dat de acceptatie start zonder op PIT te wachten, en dat een rode PIT-job de run rood maakt.
