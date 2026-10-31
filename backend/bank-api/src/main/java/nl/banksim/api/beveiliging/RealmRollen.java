package nl.banksim.api.beveiliging;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * ADR 0015: de realm-rollen van Keycloak ({@code realm_access.roles}) als rechten met {@code ROLE_}, zodat
 * {@code hasRole('beheerder')} werkt. Spring leest zelf alleen {@code scope}. Zonder rollen, of met een claim die geen
 * lijst is, zijn er geen rechten. Een eigen klasse, zodat PIT haar meet (backlog fase 3.8, ADR 0018).
 */
class RealmRollen implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        Object rollen = realmAccess == null ? null : realmAccess.get("roles");
        if (rollen instanceof Collection<?> lijst) {
            return lijst.stream()
                    .map(rol -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + rol))
                    .toList();
        }
        return List.of();
    }
}
