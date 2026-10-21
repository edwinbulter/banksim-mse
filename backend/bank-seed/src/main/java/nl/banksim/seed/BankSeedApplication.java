package nl.banksim.seed;

import java.security.SecureRandom;
import java.util.HexFormat;

import nl.banksim.domain.rekening.Iban;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/** CLI die testdata aanmaakt (ADR 0003). De shell-driver start hem als pod met {@code kubectl run}. */
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
    KeycloakLogins logins(RestClient.Builder builder, SeedProperties properties) {
        return new KeycloakLogins(builder, properties.keycloak(), () -> HexFormat.of().formatHex(bytes(24)));
    }

    @Bean
    CommandLineRunner seed(Logins logins, Rekeninghouders rekeninghouders, Transacties transacties, JsonMapper json) {
        var aanmaker = new SeedKlantAanmaker(logins, rekeninghouders,
                () -> Iban.simBank(RANDOM.nextLong(10_000_000_000L)));
        return args -> new SeedCommando(aanmaker, transacties, json, System.out).voerUit(args);
    }

    private static byte[] bytes(int aantal) {
        byte[] bytes = new byte[aantal];
        RANDOM.nextBytes(bytes);
        return bytes;
    }
}
