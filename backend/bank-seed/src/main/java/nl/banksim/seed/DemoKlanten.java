package nl.banksim.seed;

import java.util.List;

/** S11.1 (FO "Datamodel en fake data"): de klanten van de 10 huishoudens, met de namen uit het origineel. */
final class DemoKlanten {

    record DemoKlant(String gebruikersnaam, String naam) {
    }

    static final String BEHEERDER = "beheerder";

    static final List<DemoKlant> ALLE = List.of(
            new DemoKlant("jdevries", "Jan de Vries"),
            new DemoKlant("sbakker", "Sanne Bakker"),
            new DemoKlant("melamrani", "Mohamed El Amrani"),
            new DemoKlant("ljansen", "Lisa Jansen"),
            new DemoKlant("pvisser", "Pieter Visser"),
            new DemoKlant("fyilmaz", "Fatma Yilmaz"),
            new DemoKlant("dsmit", "Daan Smit"),
            new DemoKlant("edeboer", "Emma de Boer"),
            new DemoKlant("rmulder", "Ruud Mulder"),
            new DemoKlant("nhendriks", "Noor Hendriks"));

    private DemoKlanten() {
    }
}
