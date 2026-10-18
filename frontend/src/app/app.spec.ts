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
