package nl.banksim.api.transactie;

import java.util.List;
import java.util.Optional;

/** Een portie transacties, nieuw naar oud; {@code volgende} is de positie om verder te lezen, leeg aan het eind. */
record Pagina(List<Transactie> transacties, Optional<Positie> volgende) {
}
