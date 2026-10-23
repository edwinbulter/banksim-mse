package nl.banksim.api.simulatie;

import java.time.LocalDate;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** S3.1: de beheerder zet de bank op een gekozen datum, of terug op vandaag (ADR 0013). */
@RestController
class SimulatiedatumController {

    private final SimulatiedatumRepository simulatiedatum;

    SimulatiedatumController(SimulatiedatumRepository simulatiedatum) {
        this.simulatiedatum = simulatiedatum;
    }

    @PutMapping("/api/beheer/simulatiedatum")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void zet(@RequestBody SimulatiedatumJson json) {
        if (json.datum() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "datum ontbreekt");
        }
        simulatiedatum.zet(json.datum());
    }

    @DeleteMapping("/api/beheer/simulatiedatum")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void wis() {
        simulatiedatum.wis();
    }

    /** {@code {"datum": "2026-10-05"}}; een andere notatie geeft 400. */
    record SimulatiedatumJson(LocalDate datum) {
    }
}
