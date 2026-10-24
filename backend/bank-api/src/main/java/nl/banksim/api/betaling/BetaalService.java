package nl.banksim.api.betaling;

import nl.banksim.api.grootboek.Grootboek;
import nl.banksim.api.grootboek.LedgerService;
import nl.banksim.api.simulatie.SimulationClock;
import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.grootboek.Contact;
import nl.banksim.domain.grootboek.Overboeking;
import nl.banksim.domain.grootboek.Partij;
import nl.banksim.domain.rekening.Iban;
import nl.banksim.domain.transactie.TransactieType;
import org.springframework.stereotype.Service;

/** S7.1: een betaling van de eigen rekening, type Online bankieren, op het tijdstip van de bank (FO). */
@Service
class BetaalService {

    private final Grootboek grootboek;
    private final Contacten contacten;
    private final LedgerService ledger;
    private final SimulationClock klok;

    BetaalService(Grootboek grootboek, Contacten contacten, LedgerService ledger, SimulationClock klok) {
        this.grootboek = grootboek;
        this.contacten = contacten;
        this.ledger = ledger;
        this.klok = klok;
    }

    /**
     * Andermans rekening bestaat voor de klant niet (ADR 0023); alleen naar een contact, met een naam die bij het contact
     * past (FO). Eerst de eigen rekening, dan het contact: anders verraadt de weigering welke IBAN's contacten zijn.
     */
    void betaal(String gebruikersnaam, Iban van, Iban naar, String naam, Bedrag bedrag) {
        String eigenNaam = grootboek.naamBijEigenRekening(gebruikersnaam, van).orElseThrow(RekeningNietGevonden::new);
        Contact contact = contacten.vind(naar).orElseThrow(() ->
                new BetalingGeweigerd("onbekend-contact", "Naar dit rekeningnummer kan niet worden betaald."));
        if (!contact.naamPast(naam)) {
            throw new BetalingGeweigerd("naam-past-niet", "De naam van de ontvanger hoort niet bij dit rekeningnummer.");
        }
        // De tegenpartij is de naam van het contact, niet de schrijfwijze van de klant.
        ledger.boek(new Overboeking(new Partij(van, eigenNaam), new Partij(naar, contact.naam()), bedrag,
                TransactieType.ONLINE_BANKIEREN, klok.nu()));
    }
}
