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
- **Node-versie:** controleer welke versie het Angular-pakket in zijn `engines` vereist. Zet die in `.nvmrc` en in CI.
