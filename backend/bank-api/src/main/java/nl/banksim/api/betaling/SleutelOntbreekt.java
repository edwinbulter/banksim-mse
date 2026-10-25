package nl.banksim.api.betaling;

/** Een betaalverzoek zonder Idempotency-Key (ADR 0027). */
class SleutelOntbreekt extends RuntimeException {
}
