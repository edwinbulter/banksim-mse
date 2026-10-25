-- Slice 7 (S7.1, FO): de extra omschrijving van een betaling, bij beide kanten. Optioneel en niet getoond (FO).
ALTER TABLE transactie ADD COLUMN extra_omschrijving varchar(35);
