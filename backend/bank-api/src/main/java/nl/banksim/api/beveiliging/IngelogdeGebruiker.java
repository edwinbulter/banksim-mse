package nl.banksim.api.beveiliging;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/** De gebruiker van de huidige request, uit het gevalideerde JWT van realm banksim-mse (ADR 0008). */
@Component
public class IngelogdeGebruiker {

    public String gebruikersnaam() {
        if (!(SecurityContextHolder.getContext().getAuthentication().getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException("Geen ingelogde gebruiker met een JWT");
        }
        return jwt.getClaimAsString("preferred_username");
    }
}
