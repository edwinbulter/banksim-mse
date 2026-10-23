package nl.banksim.api.simulatie;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** De simulatiedatum in tabel instelling (V3), in hooguit één rij. */
@Repository
class JdbcSimulatiedatumRepository implements SimulatiedatumRepository {

    private final JdbcClient jdbc;

    JdbcSimulatiedatumRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<LocalDate> lees() {
        return jdbc.sql("SELECT simulatiedatum FROM instelling").query(LocalDate.class).optional();
    }

    @Override
    public void zet(LocalDate datum) {
        jdbc.sql("""
                        INSERT INTO instelling (simulatiedatum) VALUES (:datum)
                        ON CONFLICT (id) DO UPDATE SET simulatiedatum = excluded.simulatiedatum""")
                .param("datum", datum)
                .update();
    }

    @Override
    public void wis() {
        jdbc.sql("DELETE FROM instelling").update();
    }
}
