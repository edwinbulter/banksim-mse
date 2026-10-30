package nl.banksim.api.rente;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

import nl.banksim.api.grootboek.OverboekingGeboekt;
import nl.banksim.api.simulatie.Maandafsluiting;
import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.grootboek.Overboeking;
import nl.banksim.domain.rekening.Iban;
import nl.banksim.domain.rente.RenteCalculator;
import nl.banksim.domain.transactie.TransactieType;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * ADR 0028: de maandafsluiting schrijft de rente bij op elke spaarrekening, voor elke maand sinds het openen waarvan de
 * laatste dag uiterlijk {@code totEnMet} is. Een maand die al is afgesloten, slaat {@link MaandRente} over. De lopende
 * rente (S8.1) rekent hij uit met dezelfde dagsaldi, zonder iets te boeken.
 */
@Service
class RenteService implements Maandafsluiting, LopendeRente {

    private static final Bedrag GEEN_RENTE = Bedrag.van("0.00");

    private final RenteOpslag opslag;
    private final MaandRente maandRente;

    RenteService(RenteOpslag opslag, MaandRente maandRente) {
        this.opslag = opslag;
        this.maandRente = maandRente;
    }

    @Override
    public void sluitAf(LocalDate totEnMet) {
        for (Spaarrekening rekening : opslag.spaarrekeningen()) {
            for (YearMonth maand = YearMonth.from(rekening.geopendOp()); !maand.atEndOfMonth().isAfter(totEnMet);
                    maand = maand.plusMonths(1)) {
                maandRente.boek(rekening, maand);
            }
        }
    }

    /**
     * ADR 0030: na elke boeking op een spaarrekening de rente van die maand en de latere bijgeschreven maanden opnieuw
     * uitrekenen, maand voor maand, zodat een correctie meetelt in de maanden erna. Synchroon, in de transactie van de
     * boeking. Rente en correcties zelf (type RENTE) leiden niet tot een herberekening.
     */
    @EventListener
    void opBoeking(OverboekingGeboekt gebeurtenis) {
        Overboeking overboeking = gebeurtenis.overboeking();
        if (overboeking.type() == TransactieType.RENTE) {
            return;
        }
        for (Spaarrekening rekening : opslag.spaarrekeningen()) {
            if (rekening.iban().equals(overboeking.van().iban()) || rekening.iban().equals(overboeking.naar().iban())) {
                for (YearMonth maand = YearMonth.from(overboeking.datum()); opslag.isBijgeschreven(rekening.iban(), maand);
                        maand = maand.plusMonths(1)) {
                    maandRente.corrigeer(rekening, maand);
                }
            }
        }
    }

    /** S8.1 (FO): van de 1e van de maand, of de dag van openen, tot en met vandaag; nog niet bijgeschreven. */
    @Override
    public Optional<Bedrag> van(String gebruikersnaam, Iban iban, LocalDate vandaag) {
        return opslag.spaarrekening(gebruikersnaam, iban).map(rekening -> {
            if (rekening.geopendOp() == null) {
                return GEEN_RENTE;
            }
            return RenteCalculator.rente(opslag.dagsaldi(iban, rekening.eersteRentedag(YearMonth.from(vandaag)), vandaag));
        });
    }
}
