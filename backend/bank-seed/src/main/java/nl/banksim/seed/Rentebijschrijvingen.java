package nl.banksim.seed;

import java.time.YearMonth;
import java.util.List;

import nl.banksim.domain.rekening.Iban;

/** ADR 0043: de maanden waarvan de rente al is bijgeschreven, zodat de maandafsluiting van bank-api ze overslaat. */
public interface Rentebijschrijvingen {

    void legVast(Iban spaarrekening, List<YearMonth> maanden);
}
