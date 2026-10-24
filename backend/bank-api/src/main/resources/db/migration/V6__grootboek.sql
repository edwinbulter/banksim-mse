-- Slice 7 (ADR 0024): een overboeking tussen twee rekeningen van BankSim is één databasetransactie met precies twee
-- transacties, op twee verschillende rekeningen, met som 0. Een transactie zonder overboeking is een eenzijdige
-- beweging met de buitenwereld (seed, later de generator). Type, tijdstip en datum staan ook in elke transactie: de
-- transactielijst, zoeken en de cursor (ADR 0011) lezen alleen transactie.
CREATE TABLE overboeking (
    id       bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    type     text        NOT NULL,
    tijdstip timestamptz NOT NULL,
    datum    date        NOT NULL
);

ALTER TABLE transactie ADD COLUMN overboeking_id bigint REFERENCES overboeking (id);
CREATE INDEX transactie_overboeking ON transactie (overboeking_id) WHERE overboeking_id IS NOT NULL;

-- De laatste verdedigingslinie: bij commit, ook voor SQL buiten LedgerService om.
CREATE FUNCTION controleer_overboeking_in_balans() RETURNS trigger
    LANGUAGE plpgsql AS
$$
DECLARE
    v_overboeking bigint := COALESCE(NEW.overboeking_id, OLD.overboeking_id);
    v_aantal      int;
    v_rekeningen  int;
    v_som         numeric;
BEGIN
    IF v_overboeking IS NULL THEN
        RETURN NULL;
    END IF;
    SELECT count(*), count(DISTINCT iban), COALESCE(sum(bedrag), 0)
    INTO v_aantal, v_rekeningen, v_som
    FROM transactie
    WHERE overboeking_id = v_overboeking;
    IF v_aantal <> 2 OR v_rekeningen <> 2 OR v_som <> 0 THEN
        RAISE EXCEPTION 'Overboeking % is niet in balans: % transacties op % rekeningen, som %',
            v_overboeking, v_aantal, v_rekeningen, v_som
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER transactie_in_balans
    AFTER INSERT OR UPDATE OR DELETE ON transactie
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION controleer_overboeking_in_balans();
