-- Slice 1: rekeninghouders en hun rekeningen. Het saldo is voorlopig het openingssaldo; boekingen komen in slice 2.
CREATE TABLE rekeninghouder (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- Dezelfde gebruikersnaam als de login in Keycloak (preferred_username).
    gebruikersnaam text NOT NULL UNIQUE
);

CREATE TABLE rekening (
    iban              text PRIMARY KEY,
    rekeninghouder_id bigint NOT NULL REFERENCES rekeninghouder (id),
    soort             text NOT NULL CHECK (soort IN ('betaal', 'spaar')),
    -- ADR 0010: exact, 2 decimalen.
    openingssaldo     numeric(15, 2) NOT NULL
);

CREATE INDEX rekening_rekeninghouder ON rekening (rekeninghouder_id);
