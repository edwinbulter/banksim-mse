# Stack: Angular met Vitest

## Componenttest met HTTP

```ts
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { App } from './app';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  it('toont de versie uit de API in de voettekst', async () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    TestBed.inject(HttpTestingController).expectOne('/api/versie').flush({ versie: '1.2.3' });
    await fixture.whenStable();
    fixture.detectChanges();
    const voettekst = (fixture.nativeElement as HTMLElement).querySelector('footer');
    expect(voettekst?.textContent).toContain('Versie 1.2.3');
  });
});
```

## Outside-in in de frontend

1. **Component:** test wat de gebruiker ziet en doet. Vervang de HTTP-laag door `HttpTestingController`, of vervang een service door een test double.
2. **Service:** test de HTTP-aanroepen en de mapping met `HttpTestingController`.
3. **Pure functies:** gewone Vitest-tests, bijvoorbeeld voor formattering, validatie of berekeningen.

## Tips

- **Commit stage:** `lint`, de tests en de productie-build. Een build die faalt, valt in de acceptance stage niet meer op te lossen.
- **Semantische HTML** (`<main>`, `<footer>`, labels bij velden) maakt de page objects in de UI-driver eenvoudig. Die kunnen dan selecteren met `getByRole` en `getByLabel`.
- **Gebruik dezelfde tekst als het scenario** ("Versie …"). Verandert de tekst, dan falen de componenttest én de UI-driver. Dat is de bedoeling.
- **Witruimte tussen elementen:** Angular haalt standaard de witruimte tussen elementen weg (`preserveWhitespaces: false`). `<span>{{ iban }}</span> <span>{{ saldo }}</span>` wordt in de browser dan "NL…€ 1.842,17" aan elkaar. Gebruik `&ngsp;` tussen de elementen. Een componenttest die de hele tekst controleert, vangt dit.
- **Node-versie:** controleer welke versie het Angular-pakket in zijn `engines` vereist. Zet die in `.nvmrc` en in CI.
- **Tijdzone in tests:** Vitest draait in de tijdzone van de machine. Een opmaak zonder `timeZone` in `Intl.DateTimeFormat` blijft groen op een ontwikkelmachine in dezelfde zone als de verwachting, en faalt pas in CI (UTC). De Playwright-browsers helpen niet: `timezoneId` staat daar vaak op dezelfde zone. Zet `TZ=UTC` in het testscript (`"test": "TZ=UTC ng test --watch=false"`), en test een moment vlak na middernacht en een datum in de wintertijd.
- **Een lijstregel die uitklapt:** zet de details niet in een geneste lijst (`ul`) binnen het `li`. Page objects die `getByRole('listitem')` of de tekst van het `li` lezen, gaan dan stuk. Maak de regel een `button` met `aria-expanded`, en zet de details in een `dl` met een `aria-label`.
