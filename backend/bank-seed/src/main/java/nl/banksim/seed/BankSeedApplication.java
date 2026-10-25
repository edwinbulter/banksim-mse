package nl.banksim.seed;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.function.Supplier;

import nl.banksim.domain.rekening.Iban;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * CLI die testdata aanmaakt (ADR 0003). De shell-driver start hem als pod met {@code kubectl run}: per opdracht, of met
 * het commando {@code sessie} één keer per Playwright-worker, met de opdrachten op stdin (ADR 0020).
 */
@SpringBootApplication
@EnableConfigurationProperties(SeedProperties.class)
public class BankSeedApplication {

    private static final SecureRandom RANDOM = new SecureRandom();

    public static void main(String[] args) {
        SpringApplication.run(BankSeedApplication.class, args);
    }

    @Bean
    JdbcRekeninghouders rekeninghouders(JdbcClient jdbc) {
        return new JdbcRekeninghouders(jdbc);
    }

    @Bean
    JdbcTransacties transacties(JdbcClient jdbc) {
        return new JdbcTransacties(jdbc);
    }

    @Bean
    Contacten contacten(JdbcClient jdbc) {
        return new JdbcContacten(jdbc);
    }

    @Bean
    KeycloakLogins logins(RestClient.Builder builder, SeedProperties properties) {
        return new KeycloakLogins(builder, properties.keycloak(), () -> HexFormat.of().formatHex(bytes(24)));
    }

    @Bean
    CommandLineRunner seed(KeycloakLogins logins, Rekeninghouders rekeninghouders, Transacties transacties,
            Contacten contacten, JdbcClient jdbc, JsonMapper json) {
        Supplier<Iban> nieuweIban = () -> Iban.simBank(RANDOM.nextLong(10_000_000_000L));
        var aanmaker = new SeedKlantAanmaker(logins, rekeninghouders, nieuweIban);
        var spaarrekeningen = new JdbcSpaarrekeningen(jdbc, nieuweIban);
        return args -> {
            var commando = new SeedCommando(aanmaker, transacties, logins, contacten, spaarrekeningen, json, System.out);
            if (args.length == 1 && args[0].equals("sessie")) {
                var stdin = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
                new SeedSessie(commando, json, stdin, System.out).voerUit();
            } else {
                commando.voerUit(args);
            }
        };
    }

    private static byte[] bytes(int aantal) {
        byte[] bytes = new byte[aantal];
        RANDOM.nextBytes(bytes);
        return bytes;
    }
}
