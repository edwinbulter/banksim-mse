package nl.banksim.api.beveiliging;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** ADR 0037: wie de ingelogde gebruiker is, voor de navigatie van de frontend; de API beslist zelf over toegang. */
@RestController
class IkController {

    private final IngelogdeGebruiker gebruiker;

    IkController(IngelogdeGebruiker gebruiker) {
        this.gebruiker = gebruiker;
    }

    @GetMapping("/api/ik")
    @PreAuthorize("isAuthenticated()")
    IkJson ik(Authentication authenticatie) {
        boolean beheerder = authenticatie.getAuthorities().stream()
                .anyMatch(recht -> "ROLE_beheerder".equals(recht.getAuthority()));
        return new IkJson(gebruiker.gebruikersnaam(), beheerder);
    }

    record IkJson(String gebruikersnaam, boolean beheerder) {
    }
}
