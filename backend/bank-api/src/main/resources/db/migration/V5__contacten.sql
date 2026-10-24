-- Slice 7 (S7.1): de contactenlijst van de bank. Alleen naar een IBAN uit deze lijst mag een klant betalen, met een naam
-- die bij dat IBAN past (FO "Betalen scherm"). Een contact hoeft geen rekening bij BankSim te hebben.
CREATE TABLE contact (
    iban text PRIMARY KEY,
    naam text NOT NULL
);
