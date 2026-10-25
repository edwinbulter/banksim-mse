-- Slice 8 (S8.1): de dag waarop een rekening geopend is; de rente op een spaarrekening begint die dag. Optioneel:
-- bestaande rekeningen houden NULL.
ALTER TABLE rekening ADD COLUMN geopend_op date;
