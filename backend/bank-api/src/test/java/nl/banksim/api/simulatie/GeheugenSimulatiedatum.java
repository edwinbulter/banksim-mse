package nl.banksim.api.simulatie;

import java.time.LocalDate;
import java.util.Optional;

/** Een simulatiedatum in het geheugen, voor tests zonder database. */
public class GeheugenSimulatiedatum implements SimulatiedatumRepository {

    private LocalDate datum;

    @Override
    public Optional<LocalDate> lees() {
        return Optional.ofNullable(datum);
    }

    @Override
    public void zet(LocalDate datum) {
        this.datum = datum;
    }

    @Override
    public void wis() {
        datum = null;
    }
}
