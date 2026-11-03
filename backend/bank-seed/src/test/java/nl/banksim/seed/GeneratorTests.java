package nl.banksim.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.transactie.TransactieType;
import org.junit.jupiter.api.Test;

/** ADR 0041, FO "Transactiepatroon per maand": voorlopig het inkomen en de woonlasten, van oktober 2021 t/m december 2026. */
class GeneratorTests {

    private static final int MAANDEN = 63;

    private static DemoKlanten.DemoKlant klant(String gebruikersnaam) {
        return DemoKlanten.ALLE.stream().filter(klant -> klant.gebruikersnaam().equals(gebruikersnaam)).findFirst().orElseThrow();
    }

    private static Boeking inkomen(LocalDateTime tijdstip, String bedrag, String van) {
        return new Boeking(tijdstip, Bedrag.van(bedrag), van, TransactieType.OVERSCHRIJVING, Optional.empty());
    }

    private static Boeking woonlasten(LocalDateTime tijdstip, String bedrag, String aan) {
        return new Boeking(tijdstip, Bedrag.van(bedrag), aan, TransactieType.INCASSO, Optional.empty());
    }

    @Test
    void deWoonlastenGaanOpDeEersteEnHetInkomenKomtOpDe24e() {
        List<Boeking> boekingen = Generator.boekingen(klant("jdevries"));

        assertThat(boekingen).hasSize(2 * MAANDEN);
        assertThat(boekingen.subList(0, 2)).containsExactly(
                woonlasten(LocalDateTime.of(2021, 10, 1, 6, 0), "-950.00", "Woonstichting Thuis"),
                inkomen(LocalDateTime.of(2021, 10, 24, 7, 0), "3100.00", "Techniek Noord B.V."));
        assertThat(boekingen.subList(boekingen.size() - 2, boekingen.size())).containsExactly(
                woonlasten(LocalDateTime.of(2026, 12, 1, 6, 0), "-950.00", "Woonstichting Thuis"),
                inkomen(LocalDateTime.of(2026, 12, 24, 7, 0), "3100.00", "Techniek Noord B.V."));
    }

    @Test
    void zonderWoonlastenBlijftAlleenHetInkomen() {
        List<Boeking> boekingen = Generator.boekingen(klant("pvisser"));

        assertThat(boekingen).hasSize(MAANDEN);
        assertThat(boekingen.getFirst()).isEqualTo(inkomen(LocalDateTime.of(2021, 10, 24, 7, 0), "2450.00", "Pensioenfonds Midden"));
    }

    @Test
    void elkeKlantHeeftInElkeMaandEenBoeking() {
        for (DemoKlanten.DemoKlant klant : DemoKlanten.ALLE) {
            assertThat(Generator.boekingen(klant).stream().map(boeking -> YearMonth.from(boeking.tijdstip())).distinct())
                    .as(klant.gebruikersnaam())
                    .hasSize(MAANDEN)
                    .startsWith(YearMonth.of(2021, 10))
                    .endsWith(YearMonth.of(2026, 12));
        }
    }

    @Test
    void deBoekingenStaanOpTijdsvolgorde() {
        for (DemoKlanten.DemoKlant klant : DemoKlanten.ALLE) {
            assertThat(Generator.boekingen(klant)).as(klant.gebruikersnaam()).isSortedAccordingTo(Comparator.comparing(Boeking::tijdstip));
        }
    }

    /** ADR 0041: op elke installatiedatum dezelfde data. */
    @Test
    void tweeKeerGenererenGeeftDezelfdeBoekingen() {
        assertThat(Generator.boekingen(klant("sbakker"))).isNotEmpty().isEqualTo(Generator.boekingen(klant("sbakker")));
    }
}
