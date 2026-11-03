package nl.banksim.seed;

import java.util.List;
import java.util.Optional;

import nl.banksim.domain.geld.Bedrag;

/**
 * S11.1 (FO "Datamodel en fake data"): de klanten van de 10 huishoudens, met de namen, beginsaldi, inkomens en woonlasten uit het
 * origineel. Bij een gepensioneerde zijn pensioen en AOW samen één inkomen; Daan Smit is zzp'er, met één opdrachtgever.
 */
final class DemoKlanten {

    record VasteBoeking(String tegenpartij, Bedrag bedrag) {
    }

    record DemoKlant(String gebruikersnaam, String naam, Bedrag beginsaldo, VasteBoeking inkomen,
            Optional<VasteBoeking> woonlasten) {
    }

    static final String BEHEERDER = "beheerder";

    private static final String WOONSTICHTING = "Woonstichting Thuis";
    private static final String WONINGCORPORATIE = "Woningcorporatie Samen";
    private static final String HYPOTHEEKBANK = "Hypotheekbank Hollandia";

    static final List<DemoKlant> ALLE = List.of(
            klant("jdevries", "Jan de Vries", "1800.00", "Techniek Noord B.V.", "3100.00", WOONSTICHTING, "950.00"),
            klant("sbakker", "Sanne Bakker", "2500.00", "Zorggroep Rivierenland", "4800.00", HYPOTHEEKBANK, "1350.00"),
            klant("melamrani", "Mohamed El Amrani", "1500.00", "Logistiek Centraal B.V.", "4200.00", WONINGCORPORATIE, "1250.00"),
            klant("ljansen", "Lisa Jansen", "900.00", "Onderwijsgroep Midden", "2350.00", WOONSTICHTING, "780.00"),
            new DemoKlant("pvisser", "Pieter Visser", Bedrag.van("3000.00"),
                    new VasteBoeking("Pensioenfonds Midden", Bedrag.van("2450.00")), Optional.empty()),
            klant("fyilmaz", "Fatma Yilmaz", "2200.00", "Gemeente Zuidstad Salarisadministratie", "5200.00", HYPOTHEEKBANK, "1450.00"),
            klant("dsmit", "Daan Smit", "4000.00", "Kompas Advies B.V.", "4650.00", HYPOTHEEKBANK, "1100.00"),
            klant("edeboer", "Emma de Boer", "1700.00", "Kompas Advies B.V.", "3900.00", WONINGCORPORATIE, "1150.00"),
            klant("rmulder", "Ruud Mulder", "2000.00", "Pensioenfonds Midden", "2600.00", WOONSTICHTING, "850.00"),
            klant("nhendriks", "Noor Hendriks", "3500.00", "Bouwbedrijf Van Dijk B.V.", "6000.00", HYPOTHEEKBANK, "1600.00"));

    private DemoKlanten() {
    }

    private static DemoKlant klant(String gebruikersnaam, String naam, String beginsaldo, String inkomenVan, String inkomen,
            String woonlastenAan, String woonlasten) {
        return new DemoKlant(gebruikersnaam, naam, Bedrag.van(beginsaldo), new VasteBoeking(inkomenVan, Bedrag.van(inkomen)),
                Optional.of(new VasteBoeking(woonlastenAan, Bedrag.van(woonlasten))));
    }
}
