import { expect, type APIRequestContext } from '@playwright/test';
import type { BankDriver } from '../bank-driver';

/** Spreekt BankSim aan via de publieke API-paden achter de ingress, net als de browser. */
export class RestDriver implements BankDriver {
  constructor(private readonly request: APIRequestContext) {}

  async openStartpagina(): Promise<void> {
    const antwoord = await this.request.get('/');
    expect(antwoord.status(), 'GET /').toBe(200);
  }

  async versie(): Promise<string> {
    const antwoord = await this.request.get('/api/versie');
    expect(antwoord.status(), 'GET /api/versie').toBe(200);
    const body = (await antwoord.json()) as { versie: string };
    return body.versie;
  }
}
