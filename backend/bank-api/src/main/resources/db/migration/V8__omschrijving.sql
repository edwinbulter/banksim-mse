-- Slice 7 (S7.1, FO): de omschrijving en het betalingskenmerk van een betaling, bij beide kanten van de overboeking.
-- Optioneel; bestaande transacties hebben ze niet. De lengtes komen uit het FO.
ALTER TABLE transactie
    ADD COLUMN omschrijving     varchar(140),
    ADD COLUMN betalingskenmerk varchar(25);
