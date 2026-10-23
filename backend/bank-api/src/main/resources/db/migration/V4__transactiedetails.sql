-- Slice 4 (S4.1): de details van een transactie, en de naam van de rekeninghouder voor Van en Naar.
-- Bestaande rijen blijven geldig: de naam wordt de gebruikersnaam, een transactie wordt Online bankieren om middernacht
-- (Europe/Amsterdam) op zijn datum, zonder tegen-IBAN. De datum blijft de uitvoerdatum: sortering, cursor (ADR 0011) en
-- simulatiedatum (ADR 0013) veranderen niet.
ALTER TABLE rekeninghouder ADD COLUMN naam text;
UPDATE rekeninghouder SET naam = gebruikersnaam;
ALTER TABLE rekeninghouder ALTER COLUMN naam SET NOT NULL;

ALTER TABLE transactie
    ADD COLUMN type text NOT NULL DEFAULT 'ONLINE_BANKIEREN'
        CHECK (type IN ('ONLINE_BANKIEREN', 'IDEAL_WERO', 'BETAALAUTOMAAT', 'INCASSO', 'GELDAUTOMAAT',
                        'OVERSCHRIJVING', 'VERZAMELBETALING')),
    ADD COLUMN tijdstip timestamptz,
    -- Null bij een betaalautomaat: dan toont Naar geen IBAN.
    ADD COLUMN tegen_iban text;
UPDATE transactie SET tijdstip = datum::timestamp AT TIME ZONE 'Europe/Amsterdam';
ALTER TABLE transactie ALTER COLUMN tijdstip SET NOT NULL;
