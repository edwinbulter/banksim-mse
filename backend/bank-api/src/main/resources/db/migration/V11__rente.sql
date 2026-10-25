-- Slice 8 (S8.1, ADR 0028): rente op de spaarrekening, bijgeschreven door de maandafsluiting.
-- Het type Rente bij de transacties (de 7 typen uit V4 plus RENTE).
ALTER TABLE transactie DROP CONSTRAINT transactie_type_check;
ALTER TABLE transactie ADD CONSTRAINT transactie_type_check
    CHECK (type IN ('ONLINE_BANKIEREN', 'IDEAL_WERO', 'BETAALAUTOMAAT', 'INCASSO', 'GELDAUTOMAAT', 'OVERSCHRIJVING',
                    'VERZAMELBETALING', 'RENTE'));

-- Een rekening van BankSim zelf: zonder login, mag negatief worden (nooit rood geldt voor betaal en spaar).
ALTER TABLE rekening DROP CONSTRAINT rekening_soort_check;
ALTER TABLE rekening ADD CONSTRAINT rekening_soort_check CHECK (soort IN ('betaal', 'spaar', 'intern'));

-- De renterekening van BankSim; de gebruikersnaam heeft geen login in Keycloak.
INSERT INTO rekeninghouder (gebruikersnaam, naam) VALUES ('banksim', 'BankSim');
INSERT INTO rekening (iban, rekeninghouder_id, soort, openingssaldo)
SELECT 'NL24SIMB0000000000', id, 'intern', 0.00 FROM rekeninghouder WHERE gebruikersnaam = 'banksim';

-- Per spaarrekening en maand hooguit één bijschrijving; de maand is de eerste dag van die maand.
CREATE TABLE rentebijschrijving (
    iban  text NOT NULL REFERENCES rekening (iban),
    maand date NOT NULL,
    PRIMARY KEY (iban, maand)
);
