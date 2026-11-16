package nl.banksim.api.betaling;

/** Wat een betaling of overschrijving deed: geboekt, of een herhaling met dezelfde sleutel herkend (ADR 0027). */
enum Afhandeling {
    GEBOEKT,
    HERHAALD
}
