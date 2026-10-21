-- Slice 2: geboekte transacties per rekening. Het saldo is het openingssaldo plus de som van de transacties.
-- Eén kant per transactie; het grootboek met twee kanten per overboeking komt in slice 7.
CREATE TABLE transactie (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    iban        text NOT NULL REFERENCES rekening (iban),
    datum       date NOT NULL,
    -- ADR 0010: exact, 2 decimalen; negatief is een afschrijving.
    bedrag      numeric(15, 2) NOT NULL CHECK (bedrag <> 0),
    tegenpartij text NOT NULL
);

CREATE INDEX transactie_rekening_datum ON transactie (iban, datum DESC, id DESC);
