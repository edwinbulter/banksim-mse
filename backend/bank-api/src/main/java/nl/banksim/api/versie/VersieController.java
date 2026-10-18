package nl.banksim.api.versie;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Walking skeleton: bewijst dat de keten browser → ingress → bank-api werkt. */
@RestController
class VersieController {

    private final String versie;

    VersieController(@Value("${banksim.versie}") String versie) {
        this.versie = versie;
    }

    @GetMapping("/api/versie")
    Versie versie() {
        return new Versie(versie);
    }

    record Versie(String versie) {
    }
}
