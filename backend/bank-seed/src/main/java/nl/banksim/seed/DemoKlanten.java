package nl.banksim.seed;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import nl.banksim.domain.geld.Bedrag;

/**
 * S11.1 (FO "Datamodel en fake data"): de klanten van de 10 huishoudens, met de namen, inkomens, woonlasten en vaste lasten uit
 * het origineel. Bij een gepensioneerde zijn pensioen en AOW samen één inkomen; Daan Smit is zzp'er, met één opdrachtgever. De
 * beginsaldi komen ook uit het origineel, behalve die van Lisa Jansen en Mohamed El Amrani: die zijn hoger, omdat hun vaste
 * lasten de rekening vóór het eerste salaris anders rood maken. Het water is maandelijks (FO), in het origineel per kwartaal.
 * De inleg op de spaarrekening komt uit het origineel, zonder de stijging per jaar (ADR 0042).
 */
final class DemoKlanten {

    record VasteBoeking(String tegenpartij, Bedrag bedrag) {
    }

    /** Een incasso op een vaste dag van de maand. */
    record VasteLast(int dag, String tegenpartij, Bedrag bedrag) {
    }

    record DemoKlant(String gebruikersnaam, String naam, Bedrag beginsaldo, VasteBoeking inkomen,
            Optional<VasteBoeking> woonlasten, List<VasteLast> vasteLasten, Optional<Bedrag> inleg) {
    }

    static final String BEHEERDER = "beheerder";

    private static final String WOONSTICHTING = "Woonstichting Thuis";
    private static final String WONINGCORPORATIE = "Woningcorporatie Samen";
    private static final String HYPOTHEEKBANK = "Hypotheekbank Hollandia";

    static final List<DemoKlant> ALLE = List.of(
            klant("jdevries", "Jan de Vries", "1800.00", "Techniek Noord B.V.", "3100.00", WOONSTICHTING, "950.00",
                    vasteLasten("95.00", 1, 1, false), "400.00"),
            klant("sbakker", "Sanne Bakker", "2500.00", "Zorggroep Rivierenland", "4800.00", HYPOTHEEKBANK, "1350.00",
                    vasteLasten("140.00", 2, 2, true), "600.00"),
            klant("melamrani", "Mohamed El Amrani", "2000.00", "Logistiek Centraal B.V.", "4200.00", WONINGCORPORATIE, "1250.00",
                    vasteLasten("175.00", 5, 2, true), "250.00"),
            klant("ljansen", "Lisa Jansen", "1200.00", "Onderwijsgroep Midden", "2350.00", WOONSTICHTING, "780.00",
                    vasteLasten("75.00", 1, 1, false), "150.00"),
            new DemoKlant("pvisser", "Pieter Visser", Bedrag.van("3000.00"),
                    new VasteBoeking("Pensioenfonds Midden", Bedrag.van("2450.00")), Optional.empty(),
                    vasteLasten("150.00", 2, 2, true), Optional.of(Bedrag.van("200.00"))),
            klant("fyilmaz", "Fatma Yilmaz", "2200.00", "Gemeente Zuidstad Salarisadministratie", "5200.00", HYPOTHEEKBANK, "1450.00",
                    vasteLasten("165.00", 4, 2, true), "500.00"),
            klant("dsmit", "Daan Smit", "4000.00", "Kompas Advies B.V.", "4650.00", HYPOTHEEKBANK, "1100.00",
                    vasteLasten("105.00", 1, 1, true), "500.00"),
            klant("edeboer", "Emma de Boer", "1700.00", "Kompas Advies B.V.", "3900.00", WONINGCORPORATIE, "1150.00",
                    vasteLasten("125.00", 2, 2, false), "450.00"),
            klant("rmulder", "Ruud Mulder", "2000.00", "Pensioenfonds Midden", "2600.00", WOONSTICHTING, "850.00",
                    vasteLasten("90.00", 1, 1, false), "150.00"),
            klant("nhendriks", "Noor Hendriks", "3500.00", "Bouwbedrijf Van Dijk B.V.", "6000.00", HYPOTHEEKBANK, "1600.00",
                    vasteLasten("185.00", 4, 2, true), "800.00"));

    private DemoKlanten() {
    }

    private static DemoKlant klant(String gebruikersnaam, String naam, String beginsaldo, String inkomenVan, String inkomen,
            String woonlastenAan, String woonlasten, List<VasteLast> vasteLasten, String inleg) {
        return new DemoKlant(gebruikersnaam, naam, Bedrag.van(beginsaldo), new VasteBoeking(inkomenVan, Bedrag.van(inkomen)),
                Optional.of(new VasteBoeking(woonlastenAan, Bedrag.van(woonlasten))), vasteLasten, Optional.of(Bedrag.van(inleg)));
    }

    private static List<VasteLast> vasteLasten(String energie, int personen, int volwassenen, boolean auto) {
        return List.of(
                new VasteLast(1, "Zorgverzekeraar Gezond", maal("140.00", volwassenen)),
                new VasteLast(1, "Verzekeringen Zeker", Bedrag.van(auto ? "73.00" : "18.00")),
                new VasteLast(15, "Groenstroom Energie", Bedrag.van(energie)),
                new VasteLast(20, "NetPlus Internet", Bedrag.van("45.00")),
                new VasteLast(20, "Waterbedrijf Midden", maal("7.50", personen)),
                new VasteLast(22, "Mobiel Direct", maal("17.50", volwassenen)));
    }

    private static Bedrag maal(String bedrag, int aantal) {
        return new Bedrag(new BigDecimal(bedrag).multiply(BigDecimal.valueOf(aantal)));
    }
}
