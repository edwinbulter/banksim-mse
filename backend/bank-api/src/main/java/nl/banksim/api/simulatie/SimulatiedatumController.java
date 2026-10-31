package nl.banksim.api.simulatie;

import java.time.LocalDate;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** S3.1: de beheerder zet de simulatiedatum of wist hem; wat daarna gebeurt, staat in {@link SimulatieService}. */
@RestController
class SimulatiedatumController {

    private final SimulatieService simulatie;

    SimulatiedatumController(SimulatieService simulatie) {
        this.simulatie = simulatie;
    }

    @PutMapping("/api/beheer/simulatiedatum")
    @PreAuthorize("hasRole('beheerder')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void zet(@RequestBody SimulatiedatumJson json) {
        if (json.datum() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "datum ontbreekt");
        }
        simulatie.zet(json.datum());
    }

    @DeleteMapping("/api/beheer/simulatiedatum")
    @PreAuthorize("hasRole('beheerder')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void wis() {
        simulatie.wis();
    }

    /** {@code {"datum": "2026-10-05"}}; een andere notatie geeft 400. */
    record SimulatiedatumJson(LocalDate datum) {
    }
}
