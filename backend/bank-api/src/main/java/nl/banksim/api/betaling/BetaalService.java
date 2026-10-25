package nl.banksim.api.betaling;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

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
    void betaal(String gebruikersnaam, String sleutel, Iban van, Iban naar, String naam, Bedrag bedrag, String omschrijving,
            String betalingskenmerk, String extraOmschrijving) {
        String verzoek = hash(String.join("|", van.toString(), naar.toString(), naam, bedrag.toString(),
                String.valueOf(omschrijving), String.valueOf(betalingskenmerk), String.valueOf(extraOmschrijving)));
        Optional<String> eerder = idempotentie.claim(sleutel, gebruikersnaam, verzoek);
        if (eerder.isPresent()) {
            if (!eerder.get().equals(verzoek)) {
                throw new BetalingGeweigerd("sleutel-al-gebruikt", "Deze betaling is al met een ander verzoek verstuurd.");
            }
            return;
        }
        String eigenNaam = grootboek.naamBijEigenRekening(gebruikersnaam, van).orElseThrow(RekeningNietGevonden::new);
        Contact contact = contacten.vind(naar).orElseThrow(() ->
                new BetalingGeweigerd("onbekend-contact", "Naar dit rekeningnummer kan niet worden betaald."));
        if (!contact.naamPast(naam)) {
            throw new BetalingGeweigerd("naam-past-niet", "De naam van de ontvanger hoort niet bij dit rekeningnummer.");
        }
        // De tegenpartij is de naam van het contact, niet de schrijfwijze van de klant.
        ledger.boek(new Overboeking(new Partij(van, eigenNaam), new Partij(naar, contact.naam()), bedrag,
                TransactieType.ONLINE_BANKIEREN, klok.nu(), omschrijving, betalingskenmerk, extraOmschrijving));
    }

    /** SHA-256 in hex: genoeg om een ander verzoek met dezelfde sleutel te herkennen. */
    private static String hash(String tekst) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(tekst.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
