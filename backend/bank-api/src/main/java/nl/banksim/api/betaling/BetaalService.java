package nl.banksim.api.betaling;

import nl.banksim.api.grootboek.Grootboek;
import nl.banksim.api.grootboek.LedgerService;
import nl.banksim.api.simulatie.SimulationClock;
import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.grootboek.Overboeking;
import nl.banksim.domain.grootboek.Partij;
import nl.banksim.domain.rekening.Iban;
import nl.banksim.domain.transactie.TransactieType;
import org.springframework.stereotype.Service;

/** S7.1: een betaling van de eigen rekening, type Online bankieren, op het tijdstip van de bank (FO). */
@Service
class BetaalService {

    private final Grootboek grootboek;
    private final LedgerService ledger;
    private final SimulationClock klok;

    BetaalService(Grootboek grootboek, LedgerService ledger, SimulationClock klok) {
        this.grootboek = grootboek;
        this.ledger = ledger;
        this.klok = klok;
    }

    /** Andermans rekening bestaat voor de klant niet (ADR 0023). */
    void betaal(String gebruikersnaam, Iban van, Iban naar, String naam, Bedrag bedrag) {
        String eigenNaam = grootboek.naamBijEigenRekening(gebruikersnaam, van).orElseThrow(RekeningNietGevonden::new);
        ledger.boek(new Overboeking(new Partij(van, eigenNaam), new Partij(naar, naam), bedrag,
                TransactieType.ONLINE_BANKIEREN, klok.nu()));
    }
}
