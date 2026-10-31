package nl.banksim.api.beveiliging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/** ADR 0015: de realm-rollen van Keycloak ({@code realm_access.roles}) worden rechten met {@code ROLE_}. */
class RealmRollenTests {

    private final RealmRollen realmRollen = new RealmRollen();

    @Test
    void elkeRealmRolWordtEenRechtMetRole() {
        Jwt token = token(Map.of("realm_access", Map.of("roles", List.of("beheerder", "offline_access"))));

        assertThat(realmRollen.convert(token)).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_beheerder", "ROLE_offline_access");
    }

    @Test
    void zonderRealmAccessGeenRechten() {
        assertThat(realmRollen.convert(token(Map.of("scope", "openid")))).isEmpty();
    }

    @Test
    void zonderRolesGeenRechten() {
        assertThat(realmRollen.convert(token(Map.of("realm_access", Map.of("andere", "waarde"))))).isEmpty();
    }

    /** Bij twijfel geen rechten: een claim roles die geen lijst is, geeft geen toegang tot beheer. */
    @Test
    void rolesDieGeenLijstIsGeeftGeenRechten() {
        assertThat(realmRollen.convert(token(Map.of("realm_access", Map.of("roles", "beheerder"))))).isEmpty();
    }

    private static Jwt token(Map<String, Object> claims) {
        return Jwt.withTokenValue("token").header("alg", "none").claims(alle -> alle.putAll(claims)).build();
    }
}
