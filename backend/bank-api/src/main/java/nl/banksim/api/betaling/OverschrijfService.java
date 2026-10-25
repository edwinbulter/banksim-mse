package nl.banksim.api.betaling;

import java.util.Optional;

import nl.banksim.api.grootboek.Grootboek;
import nl.banksim.api.grootboek.LedgerService;
import nl.banksim.api.simulatie.SimulationClock;
import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.grootboek.Overboeking;
import nl.banksim.domain.grootboek.Partij;
import nl.banksim.domain.rekening.Iban;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** S9.1 (FO): een overschrijving tussen de eigen rekeningen, op het tijdstip van de bank, via LedgerService (ADR 0024). */
@Service
class OverschrijfService {

    private final Grootboek grootboek;
    private final Idempotentie idempotentie;
    private final LedgerService ledger;
    private final SimulationClock klok;

    OverschrijfService(Grootboek grootboek, Idempotentie idempotentie, LedgerService ledger, SimulationClock klok) {
        this.grootboek = grootboek;
        this.idempotentie = idempotentie;
        this.ledger = ledger;
        this.klok = klok;
    }

    /**
     * Inleggen (naar de spaarrekening) of opnemen (van de spaarrekening), met de standaardomschrijving "Inleg" of
     * "Opname" (FO).
     * Andermans rekening bestaat voor de klant niet (ADR 0023). ADR 0027: eerst de sleutel, in dezelfde
     * databasetransactie, zoals bij betalen.
     */
    @Transactional
    void schrijfOver(String gebruikersnaam, String sleutel, Iban van, Iban naar, Bedrag bedrag) {
        String verzoek = VerzoekHash.van("overschrijving", van.toString(), naar.toString(), bedrag.toString());
        Optional<String> eerder = idempotentie.claim(sleutel, gebruikersnaam, verzoek);
        if (eerder.isPresent()) {
            if (!eerder.get().equals(verzoek)) {
                throw new BetalingGeweigerd("sleutel-al-gebruikt", "Deze overschrijving is al met een ander verzoek verstuurd.");
            }
            return;
        }
        String naamVan = grootboek.naamBijEigenRekening(gebruikersnaam, van).orElseThrow(RekeningNietGevonden::new);
        String naamNaar = grootboek.naamBijEigenRekening(gebruikersnaam, naar).orElseThrow(RekeningNietGevonden::new);
        Partij vanPartij = new Partij(van, naamVan);
        Partij naarPartij = new Partij(naar, naamNaar);
        ledger.boek(grootboek.isSpaarrekening(van)
                ? Overboeking.opname(vanPartij, naarPartij, bedrag, klok.nu(), "Opname")
                : Overboeking.inleg(vanPartij, naarPartij, bedrag, klok.nu(), "Inleg"));
    }
}
