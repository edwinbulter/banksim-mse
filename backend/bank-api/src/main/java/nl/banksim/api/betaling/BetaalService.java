package nl.banksim.api.betaling;

import java.util.Optional;

import nl.banksim.api.grootboek.Grootboek;
import nl.banksim.api.grootboek.LedgerService;
import nl.banksim.api.simulatie.SimulationClock;
import nl.banksim.domain.grootboek.Contact;
import nl.banksim.domain.grootboek.Overboeking;
import nl.banksim.domain.grootboek.Partij;
import nl.banksim.domain.rekening.Iban;
import nl.banksim.domain.transactie.TransactieType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** S7.1: een betaling van de eigen rekening, type Online bankieren, op het tijdstip van de bank (FO). */
@Service
class BetaalService {

    private final Grootboek grootboek;
    private final Contacten contacten;
    private final Idempotentie idempotentie;
    private final LedgerService ledger;
    private final SimulationClock klok;

    BetaalService(Grootboek grootboek, Contacten contacten, Idempotentie idempotentie, LedgerService ledger,
            SimulationClock klok) {
        this.grootboek = grootboek;
        this.contacten = contacten;
        this.idempotentie = idempotentie;
        this.ledger = ledger;
        this.klok = klok;
    }

    /**
     * Andermans rekening bestaat voor de klant niet (ADR 0023); alleen naar een contact, met een naam die bij het contact
     * past (FO). Eerst de eigen rekening, dan het contact: anders verraadt de weigering welke IBAN's contacten zijn.
     * ADR 0027: als eerste de sleutel, in dezelfde databasetransactie. Een herhaling boekt niet opnieuw; een weigering
     * rolt ook de sleutel terug, zodat een nieuwe poging opnieuw beoordeeld wordt.
     */
    @Transactional
    void betaal(String gebruikersnaam, String sleutel, Betaalopdracht opdracht) {
        Iban van = opdracht.van();
        Iban naar = opdracht.naar();
        String verzoek = VerzoekHash.van(van.toString(), naar.toString(), opdracht.naam(), opdracht.bedrag().toString(),
                String.valueOf(opdracht.omschrijving()), String.valueOf(opdracht.betalingskenmerk()),
                String.valueOf(opdracht.extraOmschrijving()));
        Optional<String> eerder = idempotentie.claim(sleutel, gebruikersnaam, verzoek);
        if (eerder.isPresent()) {
            if (!eerder.get().equals(verzoek)) {
                throw new BetalingGeweigerd(Weigering.SLEUTEL_AL_GEBRUIKT,
                        "Deze betaling is al met een ander verzoek verstuurd.");
            }
            return;
        }
        String eigenNaam = grootboek.naamBijEigenRekening(gebruikersnaam, van).orElseThrow(RekeningNietGevonden::new);
        Contact contact = contacten.vind(naar).orElseThrow(() ->
                new BetalingGeweigerd(Weigering.ONBEKEND_CONTACT, "Naar dit rekeningnummer kan niet worden betaald."));
        if (!contact.naamPast(opdracht.naam())) {
            throw new BetalingGeweigerd(Weigering.NAAM_PAST_NIET,
                    "De naam van de ontvanger hoort niet bij dit rekeningnummer.");
        }
        // De tegenpartij is de naam van het contact, niet de schrijfwijze van de klant.
        ledger.boek(new Overboeking(new Partij(van, eigenNaam), new Partij(naar, contact.naam()), opdracht.bedrag(),
                TransactieType.ONLINE_BANKIEREN, klok.nu(), opdracht.omschrijving(), opdracht.betalingskenmerk(),
                opdracht.extraOmschrijving()));
    }
}
