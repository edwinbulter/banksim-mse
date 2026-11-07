package nl.banksim.seed;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import nl.banksim.domain.geld.Bedrag;

/**
 * S11.1 (FO "Datamodel en fake data"): de klanten van de 10 huishoudens, met de namen, inkomens, woonlasten en vaste lasten uit
 * het origineel. Bij een gepensioneerde zijn pensioen en AOW samen één inkomen; Daan Smit is zzp'er, met één opdrachtgever. Alleen een werknemer krijgt vakantiegeld; alleen een zzp'er krijgt geen belastingteruggave. De
 * beginsaldi van de betaal- en spaarrekening komen ook uit het origineel. Het water is maandelijks (FO), in het origineel per kwartaal.
 * De inleg op de spaarrekening komt uit het origineel, zonder de stijging per jaar (ADR 0042).
 */
final class DemoKlanten {

    record VasteBoeking(String tegenpartij, Bedrag bedrag) {
    }

    /** Bepaalt of de woonlasten met de inflatie meestijgen: de huur wel, de hypotheek niet. */
    enum Woonvorm {
        HUUR, HYPOTHEEK
    }

    /** De huur of de hypotheek, op de 1e van de maand. */
    record Woonlasten(Woonvorm vorm, String tegenpartij, Bedrag bedrag) {
    }

    /** Hoe een vaste last per jaar stijgt: met de inflatie, of met de energieprijzen. */
    enum Prijsreeks {
        INFLATIE, ENERGIE
    }

    /** Een incasso op een vaste dag van de maand, met het bedrag van 2021. */
    record VasteLast(int dag, String tegenpartij, Bedrag bedrag, Prijsreeks prijsreeks) {

        /** Een vaste last die met de inflatie stijgt. */
        VasteLast(int dag, String tegenpartij, Bedrag bedrag) {
            this(dag, tegenpartij, bedrag, Prijsreeks.INFLATIE);
        }
    }

    /** Bepaalt wie vakantiegeld en belastingteruggave krijgt. */
    enum Soort {
        WERKNEMER, GEPENSIONEERD, ZZP
    }

    record DemoKlant(String gebruikersnaam, String naam, Bedrag beginsaldo, Bedrag beginsaldoSpaar, VasteBoeking inkomen,
            Optional<Woonlasten> woonlasten, List<VasteLast> vasteLasten, Optional<Bedrag> inleg, Soort soort,
            Optional<Bedrag> termijnGemeente) {

        /** Een zzp'er: zonder vakantiegeld en belastingteruggave, en zonder gemeentelijke belastingen. */
        DemoKlant(String gebruikersnaam, String naam, Bedrag beginsaldo, Bedrag beginsaldoSpaar, VasteBoeking inkomen,
                Optional<Woonlasten> woonlasten, List<VasteLast> vasteLasten, Optional<Bedrag> inleg) {
            this(gebruikersnaam, naam, beginsaldo, beginsaldoSpaar, inkomen, woonlasten, vasteLasten, inleg, Soort.ZZP, Optional.empty());
        }
    }

    static final String BEHEERDER = "beheerder";

    private static final String WOONSTICHTING = "Woonstichting Thuis";
    private static final String WONINGCORPORATIE = "Woningcorporatie Samen";
    private static final String HYPOTHEEKBANK = "Hypotheekbank Hollandia";
    /** Origineel: de termijn van de gemeentelijke belastingen voor iemand die alleen woont, en voor een huishouden van meer personen. */
    private static final String ALLEENWONEND = "38.00";
    private static final String MEERPERSOONS = "55.00";

    static final List<DemoKlant> ALLE = List.of(
            klant(Soort.WERKNEMER, "jdevries", "Jan de Vries", "1800.00", "6500.00", "Techniek Noord B.V.", "3100.00", huur(WOONSTICHTING, "950.00"),
                    vasteLasten("95.00", 1, 1, false), "400.00", ALLEENWONEND),
            klant(Soort.WERKNEMER, "sbakker", "Sanne Bakker", "2500.00", "14000.00", "Zorggroep Rivierenland", "4800.00", hypotheek("1350.00"),
                    vasteLasten("140.00", 2, 2, true), "600.00", MEERPERSOONS),
            klant(Soort.WERKNEMER, "melamrani", "Mohamed El Amrani", "1500.00", "4500.00", "Logistiek Centraal B.V.", "4200.00", huur(WONINGCORPORATIE, "1250.00"),
                    vasteLasten("175.00", 5, 2, true), "250.00", MEERPERSOONS),
            klant(Soort.WERKNEMER, "ljansen", "Lisa Jansen", "900.00", "1800.00", "Onderwijsgroep Midden", "2350.00", huur(WOONSTICHTING, "780.00"),
                    vasteLasten("75.00", 1, 1, false), "150.00", ALLEENWONEND),
            new DemoKlant("pvisser", "Pieter Visser", Bedrag.van("3000.00"), Bedrag.van("38000.00"),
                    new VasteBoeking("Pensioenfonds Midden", Bedrag.van("2450.00")), Optional.empty(),
                    vasteLasten("150.00", 2, 2, true), Optional.of(Bedrag.van("200.00")), Soort.GEPENSIONEERD, Optional.of(Bedrag.van(MEERPERSOONS))),
            klant(Soort.WERKNEMER, "fyilmaz", "Fatma Yilmaz", "2200.00", "16000.00", "Gemeente Zuidstad Salarisadministratie", "5200.00", hypotheek("1450.00"),
                    vasteLasten("165.00", 4, 2, true), "500.00", MEERPERSOONS),
            klant(Soort.ZZP, "dsmit", "Daan Smit", "4000.00", "12000.00", "Kompas Advies B.V.", "4650.00", hypotheek("1100.00"),
                    vasteLasten("105.00", 1, 1, true), "500.00", ALLEENWONEND),
            klant(Soort.WERKNEMER, "edeboer", "Emma de Boer", "1700.00", "9000.00", "Kompas Advies B.V.", "3900.00", huur(WONINGCORPORATIE, "1150.00"),
                    vasteLasten("125.00", 2, 2, false), "450.00", MEERPERSOONS),
            klant(Soort.GEPENSIONEERD, "rmulder", "Ruud Mulder", "2000.00", "22000.00", "Pensioenfonds Midden", "2600.00", huur(WOONSTICHTING, "850.00"),
                    vasteLasten("90.00", 1, 1, false), "150.00", ALLEENWONEND),
            klant(Soort.WERKNEMER, "nhendriks", "Noor Hendriks", "3500.00", "25000.00", "Bouwbedrijf Van Dijk B.V.", "6000.00", hypotheek("1600.00"),
                    vasteLasten("185.00", 4, 2, true), "800.00", MEERPERSOONS));

    private DemoKlanten() {
    }

    private static DemoKlant klant(Soort soort, String gebruikersnaam, String naam, String beginsaldo, String beginsaldoSpaar, String inkomenVan, String inkomen,
            Woonlasten woonlasten, List<VasteLast> vasteLasten, String inleg, String termijnGemeente) {
        return new DemoKlant(gebruikersnaam, naam, Bedrag.van(beginsaldo), Bedrag.van(beginsaldoSpaar), new VasteBoeking(inkomenVan, Bedrag.van(inkomen)),
                Optional.of(woonlasten), vasteLasten, Optional.of(Bedrag.van(inleg)), soort, Optional.of(Bedrag.van(termijnGemeente)));
    }

    private static Woonlasten huur(String aan, String bedrag) {
        return new Woonlasten(Woonvorm.HUUR, aan, Bedrag.van(bedrag));
    }

    private static Woonlasten hypotheek(String bedrag) {
        return new Woonlasten(Woonvorm.HYPOTHEEK, HYPOTHEEKBANK, Bedrag.van(bedrag));
    }

    private static List<VasteLast> vasteLasten(String energie, int personen, int volwassenen, boolean auto) {
        return List.of(
                new VasteLast(1, "Zorgverzekeraar Gezond", maal("140.00", volwassenen)),
                new VasteLast(1, "Verzekeringen Zeker", Bedrag.van(auto ? "73.00" : "18.00")),
                new VasteLast(15, "Groenstroom Energie", Bedrag.van(energie), Prijsreeks.ENERGIE),
                new VasteLast(20, "NetPlus Internet", Bedrag.van("45.00")),
                new VasteLast(20, "Waterbedrijf Midden", maal("7.50", personen)),
                new VasteLast(22, "Mobiel Direct", maal("17.50", volwassenen)));
    }

    private static Bedrag maal(String bedrag, int aantal) {
        return new Bedrag(new BigDecimal(bedrag).multiply(BigDecimal.valueOf(aantal)));
    }
}
