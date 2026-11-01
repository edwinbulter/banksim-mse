-- ADR 0035: binnen een dag op tijdstip. De index volgt de sortering (datum, tijdstip, id), net als in ADR 0011.
DROP INDEX transactie_rekening_datum;
CREATE INDEX transactie_rekening_tijdstip ON transactie (iban, datum DESC, tijdstip DESC, id DESC);
