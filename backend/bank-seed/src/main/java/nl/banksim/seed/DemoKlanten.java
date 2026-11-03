package nl.banksim.seed;

import java.util.List;
import java.util.Optional;

import nl.banksim.domain.geld.Bedrag;

/**
 * S11.1 (FO "Datamodel en fake data"): de klanten van de 10 huishoudens, met de namen, inkomens en woonlasten uit het
 * origineel. Bij een gepensioneerde zijn pensioen en AOW samen één inkomen; Daan Smit is zzp'er, met één opdrachtgever.
 */
final class DemoKlanten {

    record VasteBoeking(String tegenpartij, Bedrag bedrag) {
    }

    record DemoKlant(String gebruikersnaam, String naam, VasteBoeking inkomen, Optional<VasteBoeking> woonlasten) {
    }

    static final String BEHEERDER = "beheerder";

    private static final String WOONSTICHTING = "Woonstichting Thuis";
    private static final String WONINGCORPORATIE = "Woningcorporatie Samen";
    private static final String HYPOTHEEKBANK = "Hypotheekbank Hollandia";

    static final List<DemoKlant> ALLE = List.of(
            klant("jdevries", "Jan de Vries", "Techniek Noord B.V.", "3100.00", WOONSTICHTING, "950.00"),
            klant("sbakker", "Sanne Bakker", "Zorggroep Rivierenland", "4800.00", HYPOTHEEKBANK, "1350.00"),
            klant("melamrani", "Mohamed El Amrani", "Logistiek Centraal B.V.", "4200.00", WONINGCORPORATIE, "1250.00"),
            klant("ljansen", "Lisa Jansen", "Onderwijsgroep Midden", "2350.00", WOONSTICHTING, "780.00"),
            new DemoKlant("pvisser", "Pieter Visser", new VasteBoeking("Pensioenfonds Midden", Bedrag.van("2450.00")), Optional.empty()),
            klant("fyilmaz", "Fatma Yilmaz", "Gemeente Zuidstad Salarisadministratie", "5200.00", HYPOTHEEKBANK, "1450.00"),
            klant("dsmit", "Daan Smit", "Kompas Advies B.V.", "4650.00", HYPOTHEEKBANK, "1100.00"),
            klant("edeboer", "Emma de Boer", "Kompas Advies B.V.", "3900.00", WONINGCORPORATIE, "1150.00"),
            klant("rmulder", "Ruud Mulder", "Pensioenfonds Midden", "2600.00", WOONSTICHTING, "850.00"),
            klant("nhendriks", "Noor Hendriks", "Bouwbedrijf Van Dijk B.V.", "6000.00", HYPOTHEEKBANK, "1600.00"));

    private DemoKlanten() {
    }

    private static DemoKlant klant(String gebruikersnaam, String naam, String inkomenVan, String inkomen, String woonlastenAan,
            String woonlasten) {
        return new DemoKlant(gebruikersnaam, naam, new VasteBoeking(inkomenVan, Bedrag.van(inkomen)),
                Optional.of(new VasteBoeking(woonlastenAan, Bedrag.van(woonlasten))));
    }
}
