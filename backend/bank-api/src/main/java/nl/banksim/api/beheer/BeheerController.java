package nl.banksim.api.beheer;

import java.util.List;

import nl.banksim.api.simulatie.SimulationClock;
import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.rekening.Iban;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** S10.1 (FO "Admin scherm"): de beheerder zoekt rekeninghouders, alleen-lezen, met de saldi op "vandaag" (ADR 0013). */
@RestController
class BeheerController {

    private final Rekeninghouders rekeninghouders;
    private final SimulationClock klok;

    BeheerController(Rekeninghouders rekeninghouders, SimulationClock klok) {
        this.rekeninghouders = rekeninghouders;
        this.klok = klok;
    }

    @GetMapping("/api/beheer/rekeninghouders")
    @PreAuthorize("hasRole('beheerder')")
    List<RekeninghouderJson> rekeninghouders(@RequestParam(defaultValue = "") String zoek) {
        return rekeninghouders.zoek(zoek, klok.vandaag()).stream().map(RekeninghouderJson::van).toList();
    }

    /** ADR 0010: bedragen als string; zonder spaarrekening zijn spaarrekening en spaarsaldo null. */
    record RekeninghouderJson(String naam, String betaalrekening, String saldo, String spaarrekening, String spaarsaldo) {

        static RekeninghouderJson van(Rekeninghouder rekeninghouder) {
            return new RekeninghouderJson(rekeninghouder.naam(), rekeninghouder.betaalrekening().toString(),
                    rekeninghouder.saldo().toString(), rekeninghouder.spaarrekening().map(Iban::toString).orElse(null),
                    rekeninghouder.spaarsaldo().map(Bedrag::toString).orElse(null));
        }
    }
}
