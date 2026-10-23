package nl.banksim.api.transactie;

import java.util.Optional;

import nl.banksim.domain.rekening.Iban;

/** Een kant van een transactie: de naam, en het IBAN als dat er is (niet bij een betaalautomaat). */
record Partij(String naam, Optional<Iban> iban) {
}
