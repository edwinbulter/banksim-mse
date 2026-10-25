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

/**
 * S3.1: de beheerder zet de bank op een gekozen datum, of terug op vandaag (ADR 0013). Daarna sluit de bank de maanden
 * af die tot die dag voorbij zijn, met de rente op de spaarrekeningen (ADR 0028).
 */
@RestController
class SimulatiedatumController {

    private final SimulatiedatumRepository simulatiedatum;
    private final Maandafsluiting maandafsluiting;
    private final SimulationClock klok;

    SimulatiedatumController(SimulatiedatumRepository simulatiedatum, Maandafsluiting maandafsluiting, SimulationClock klok) {
        this.simulatiedatum = simulatiedatum;
        this.maandafsluiting = maandafsluiting;
        this.klok = klok;
    }

    @PutMapping("/api/beheer/simulatiedatum")
    @PreAuthorize("hasRole('beheerder')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void zet(@RequestBody SimulatiedatumJson json) {
        if (json.datum() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "datum ontbreekt");
        }
        simulatiedatum.zet(json.datum());
        maandafsluiting.sluitAf(klok.vandaag());
    }

    @DeleteMapping("/api/beheer/simulatiedatum")
    @PreAuthorize("hasRole('beheerder')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void wis() {
        simulatiedatum.wis();
        maandafsluiting.sluitAf(klok.vandaag());
    }

    /** {@code {"datum": "2026-10-05"}}; een andere notatie geeft 400. */
    record SimulatiedatumJson(LocalDate datum) {
    }
}
