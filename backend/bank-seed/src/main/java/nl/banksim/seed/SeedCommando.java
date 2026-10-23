package nl.banksim.seed;

import java.io.PrintStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.rekening.Iban;
import nl.banksim.domain.transactie.TransactieType;
import tools.jackson.databind.json.JsonMapper;

/**
 * De opdrachtregel van bank-seed, bijvoorbeeld {@code klant --alias jan-f4de79-1 --betaalrekening 1842.17} of
 * {@code transactie --iban NL94SIMB0000000001 --datum 2026-10-01 --bedrag -812.40 --tegenpartij "Woningstichting Thuis"}.
 * Veel transacties in één keer: {@code transacties --iban … --transactie "2026-10-01,-812.40,Woningstichting Thuis" …}.
 * Een beheerder (ADR 0015): {@code beheerder --alias beheerder-f4de79-1}. Het
 * resultaat komt als één JSON-regel op stdout; de shell-driver leest de laatste regel (ADR 0003).
 */
class SeedCommando {

    private final KlantAanmaker klanten;
    private final Transacties transacties;
    private final Beheerders beheerders;
    private final JsonMapper json;
    private final PrintStream uit;

    SeedCommando(KlantAanmaker klanten, Transacties transacties, Beheerders beheerders, JsonMapper json, PrintStream uit) {
        this.klanten = klanten;
        this.transacties = transacties;
        this.beheerders = beheerders;
        this.json = json;
        this.uit = uit;
    }

    void voerUit(String... args) {
        String commando = args.length == 0 ? "" : args[0];
        // Na het commando komen paren "--optie waarde"; een even aantal argumenten betekent een losse optie aan het eind.
        if (args.length > 0 && args.length % 2 == 0) {
            throw new IllegalArgumentException("Optie " + args[args.length - 1] + " heeft geen waarde");
        }
        Map<String, String> opties = opties(args);
        Object resultaat = switch (commando) {
            case "klant" -> klant(opties);
            case "transactie" -> transactie(opties);
            case "transacties" -> transacties(args);
            case "beheerder" -> beheerder(opties);
            default -> throw new IllegalArgumentException("Onbekend commando: " + String.join(" ", args)
                    + " (verwacht: klant, transactie, transacties of beheerder)");
        };
        uit.println(json.writeValueAsString(resultaat));
    }

    private GeseedeKlant klant(Map<String, String> opties) {
        String alias = verplicht(opties, "--alias");
        Bedrag saldo = Bedrag.van(verplicht(opties, "--betaalrekening"));
        return klanten.klantMetBetaalrekening(alias, opties.getOrDefault("--naam", alias), saldo);
    }

    private GeseedeBeheerder beheerder(Map<String, String> opties) {
        String alias = verplicht(opties, "--alias");
        return new GeseedeBeheerder(alias, beheerders.maakBeheerder(alias));
    }

    /** Het resultaat van {@code beheerder}: de login, zonder rekeninghouder of rekening (ADR 0015). */
    record GeseedeBeheerder(String gebruikersnaam, String wachtwoord) {
    }

    /**
     * Eén transactie, op een {@code --datum} (middernacht) of een {@code --tijdstip} in Europe/Amsterdam; optioneel
     * {@code --type} (standaard Online bankieren) en {@code --tegen-iban} (S4.1).
     */
    private GeboekteTransactie transactie(Map<String, String> opties) {
        Iban iban = Iban.van(verplicht(opties, "--iban"));
        LocalDateTime tijdstip = tijdstip(opties);
        Bedrag bedrag = Bedrag.van(verplicht(opties, "--bedrag"));
        String tegenpartij = verplicht(opties, "--tegenpartij");
        TransactieType type = type(opties.getOrDefault("--type", TransactieType.ONLINE_BANKIEREN.name()));
        Optional<Iban> tegenIban = Optional.ofNullable(opties.get("--tegen-iban")).map(Iban::van);
        Boeking boeking = new Boeking(tijdstip, bedrag, tegenpartij, type, tegenIban);
        transacties.boek(iban, boeking);
        return new GeboekteTransactie(iban.toString(), boeking.datum().toString(), bedrag.toString(), tegenpartij);
    }

    private static LocalDateTime tijdstip(Map<String, String> opties) {
        String datum = opties.get("--datum");
        String tijdstip = opties.get("--tijdstip");
        if ((datum == null) == (tijdstip == null)) {
            throw new IllegalArgumentException("Geef --datum of --tijdstip, niet allebei en niet geen van beide");
        }
        return datum != null ? LocalDate.parse(datum).atStartOfDay() : LocalDateTime.parse(tijdstip);
    }

    private static TransactieType type(String naam) {
        try {
            return TransactieType.valueOf(naam);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Onbekend --type: " + naam + " (verwacht een van "
                    + Arrays.toString(TransactieType.values()) + ")");
        }
    }

    /**
     * Boekt de {@code --transactie}- en {@code --boeking}-waarden in de gegeven volgorde, maar pas als ze allemaal geldig
     * zijn. Een {@code --transactie} is "datum,bedrag,tegenpartij"; een {@code --boeking} (S5.1) is
     * "tijdstip,bedrag,type,tegenIban,tegenpartij", met een leeg tegenIban als er geen IBAN is. De tegenpartij staat
     * achteraan en mag zelf komma's bevatten.
     */
    private GeboekteTransacties transacties(String[] args) {
        Iban iban = Iban.van(verplicht(opties(args), "--iban"));
        List<Boeking> boekingen = new ArrayList<>();
        for (int i = 1; i < args.length; i += 2) {
            if (args[i].equals("--transactie")) {
                Regel regel = Regel.van(args[i + 1]);
                boekingen.add(Boeking.opDatum(regel.datum(), regel.bedrag(), regel.tegenpartij()));
            } else if (args[i].equals("--boeking")) {
                boekingen.add(boeking(args[i + 1]));
            }
        }
        boekingen.forEach(boeking -> transacties.boek(iban, boeking));
        return new GeboekteTransacties(iban.toString(), boekingen.size());
    }

    private static Boeking boeking(String waarde) {
        String[] delen = waarde.split(",", 5);
        if (delen.length < 5 || delen[4].isBlank()) {
            throw new IllegalArgumentException("--boeking verwacht tijdstip,bedrag,type,tegenIban,tegenpartij: " + waarde);
        }
        Optional<Iban> tegenIban = delen[3].isEmpty() ? Optional.empty() : Optional.of(Iban.van(delen[3]));
        return new Boeking(LocalDateTime.parse(delen[0]), Bedrag.van(delen[1]), delen[4], type(delen[2]), tegenIban);
    }

    private record Regel(LocalDate datum, Bedrag bedrag, String tegenpartij) {

        static Regel van(String waarde) {
            String[] delen = waarde.split(",", 3);
            if (delen.length < 3 || delen[2].isBlank()) {
                throw new IllegalArgumentException("--transactie verwacht datum,bedrag,tegenpartij: " + waarde);
            }
            return new Regel(LocalDate.parse(delen[0]), Bedrag.van(delen[1]), delen[2]);
        }
    }

    /** Het resultaat van {@code transacties}: hoeveel er op de rekening geboekt zijn. */
    record GeboekteTransacties(String iban, int aantal) {
    }

    /** Het resultaat van {@code transactie}: wat er geboekt is. */
    record GeboekteTransactie(String iban, String datum, String bedrag, String tegenpartij) {
    }

    private static Map<String, String> opties(String[] args) {
        Map<String, String> opties = new HashMap<>();
        for (int i = 1; i < args.length; i += 2) {
            opties.put(args[i], args[i + 1]);
        }
        return opties;
    }

    private static String verplicht(Map<String, String> opties, String naam) {
        String waarde = opties.get(naam);
        if (waarde == null) {
            throw new IllegalArgumentException("Optie " + naam + " ontbreekt");
        }
        return waarde;
    }
}
