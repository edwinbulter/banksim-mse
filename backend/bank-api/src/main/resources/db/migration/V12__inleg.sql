-- Slice 9 (S9.1, ADR 0029): een overschrijving heeft een type per kant. Op de spaarrekening staat een Inleg.
ALTER TABLE transactie DROP CONSTRAINT transactie_type_check;
ALTER TABLE transactie ADD CONSTRAINT transactie_type_check
    CHECK (type IN ('ONLINE_BANKIEREN', 'IDEAL_WERO', 'BETAALAUTOMAAT', 'INCASSO', 'GELDAUTOMAAT', 'OVERSCHRIJVING',
                    'VERZAMELBETALING', 'RENTE', 'INLEG'));
