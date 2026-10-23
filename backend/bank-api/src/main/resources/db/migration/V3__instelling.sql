-- Slice 3 (ADR 0013): instellingen van de hele bank, in hooguit één rij. Geen rij: geen simulatiedatum, de bank
-- draait op vandaag.
CREATE TABLE instelling (
    id             boolean PRIMARY KEY DEFAULT true CHECK (id),
    simulatiedatum date NOT NULL
);
