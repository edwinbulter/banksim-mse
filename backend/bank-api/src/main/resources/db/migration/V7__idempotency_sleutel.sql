-- Slice 7 (ADR 0027): de Idempotency-Key van een betaling, per gebruiker, met de hash van het verzoek. Vastgelegd in
-- de databasetransactie van de boeking: een geweigerde betaling rolt ook de sleutel terug. Het antwoord op een
-- herhaling is altijd 201 (alleen geslaagde betalingen blijven bewaard), dus alleen de hash staat hier.
CREATE TABLE idempotency_sleutel (
    sleutel        text        NOT NULL,
    gebruikersnaam text        NOT NULL,
    verzoek_hash   text        NOT NULL,
    aangemaakt_op  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (sleutel, gebruikersnaam)
);
