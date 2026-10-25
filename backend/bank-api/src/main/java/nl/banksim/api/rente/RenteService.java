package nl.banksim.api.rente;

import java.time.LocalDate;
import java.time.YearMonth;

import nl.banksim.api.simulatie.Maandafsluiting;
import org.springframework.stereotype.Service;

/**
 * ADR 0028: de maandafsluiting schrijft de rente bij op elke spaarrekening, voor elke maand sinds het openen waarvan de
 * laatste dag uiterlijk {@code totEnMet} is. Een maand die al is afgesloten, slaat {@link MaandRente} over.
 */
@Service
class RenteService implements Maandafsluiting {

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
}
