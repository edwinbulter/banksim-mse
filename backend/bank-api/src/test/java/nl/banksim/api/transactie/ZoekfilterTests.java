package nl.banksim.api.transactie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.transactie.TransactieType;
import org.junit.jupiter.api.Test;

/** S5.1: de zoekvelden zoals de klant ze invult, gecontroleerd vóór er een query is. */
class ZoekfilterTests {

    @Test
    void legeVeldenDoenNietMee() {
        Zoekfilter filter = Zoekfilter.van(Optional.of("  "), Optional.of(""), Optional.empty(), Optional.empty(),
                Optional.empty());

        assertThat(filter).isEqualTo(Zoekfilter.GEEN);
    }

    @Test
    void eenBedragMagEenKommaHebbenEnHoeftGeenCentenTeHebben() {
        Zoekfilter filter = Zoekfilter.van(Optional.empty(), Optional.of("27,95"), Optional.of("100"), Optional.empty(),
                Optional.empty());

        assertThat(filter.van()).contains(Bedrag.van("27.95"));
        assertThat(filter.tm()).contains(Bedrag.van("100.00"));
    }

    @Test
    void vanMagGelijkZijnAanTm() {
        Zoekfilter filter = Zoekfilter.van(Optional.empty(), Optional.of("27,95"), Optional.of("27.95"), Optional.empty(),
                Optional.empty());

        assertThat(filter.van()).isEqualTo(filter.tm());
    }

    @Test
    void vanGroterDanTmIsEenOngeldigBedragbereik() {
        assertThatThrownBy(() -> Zoekfilter.van(Optional.empty(), Optional.of("100"), Optional.of("50"), Optional.empty(),
                Optional.empty()))
                .isInstanceOfSatisfying(OngeldigZoekfilter.class,
                        fout -> assertThat(fout.probleem()).isEqualTo("ongeldig-bedragbereik"))
                .hasMessage("Bedrag van mag niet groter zijn dan Bedrag t/m");
    }

    @Test
    void eenBedragDatGeenBedragIsOfNegatiefIsWordtGeweigerd() {
        for (String bedrag : new String[] {"vijftig", "-50", "1,234", "12,5,0"}) {
            assertThatThrownBy(() -> Zoekfilter.van(Optional.empty(), Optional.of(bedrag), Optional.empty(),
                    Optional.empty(), Optional.empty()))
                    .as(bedrag)
                    .isInstanceOfSatisfying(OngeldigZoekfilter.class,
                            fout -> assertThat(fout.probleem()).isEqualTo("ongeldig-bedrag"));
        }
    }

    @Test
    void typeEnRichtingKomenUitEenVasteLijst() {
        Zoekfilter filter = Zoekfilter.van(Optional.empty(), Optional.empty(), Optional.empty(), Optional.of("INCASSO"),
                Optional.of("inkomend"));

        assertThat(filter.type()).contains(TransactieType.INCASSO);
        assertThat(filter.richting()).contains(Zoekfilter.Richting.INKOMEND);
        assertThatThrownBy(() -> Zoekfilter.van(Optional.empty(), Optional.empty(), Optional.empty(), Optional.of("CONTANT"),
                Optional.empty()))
                .isInstanceOfSatisfying(OngeldigZoekfilter.class, fout -> assertThat(fout.probleem()).isEqualTo("ongeldig-type"));
        assertThatThrownBy(() -> Zoekfilter.van(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of("opzij")))
                .isInstanceOfSatisfying(OngeldigZoekfilter.class,
                        fout -> assertThat(fout.probleem()).isEqualTo("ongeldige-richting"));
    }

    /** De tekst is een letterlijke deeltekst: % en _ zijn geen jokertekens, \ is geen escape (injectie, slice 5). */
    @Test
    void deTekstWordtEenLetterlijkPatroon() {
        Zoekfilter filter = Zoekfilter.van(Optional.of(" 50%_\\a "), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty());

        assertThat(filter.tekstPatroon()).contains("%50\\%\\_\\\\a%");
    }

    /** Een IBAN mag met spaties en in kleine letters; de vergelijking gebeurt zonder spaties en in hoofdletters. */
    @Test
    void hetIbanPatroonIsZonderSpatiesEnInHoofdletters() {
        Zoekfilter filter = Zoekfilter.van(Optional.of("nl91 abna 0417"), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty());

        assertThat(filter.ibanPatroon()).contains("%NL91ABNA0417%");
    }
}
