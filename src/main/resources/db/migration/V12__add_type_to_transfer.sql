ALTER TABLE transfers ADD COLUMN type VARCHAR(20);

-- проставляем тип для уже существующих записей на основе логики
UPDATE transfers SET type = 'REVERSAL' WHERE reversal_of_id IS NOT NULL;
UPDATE transfers SET type = 'OWN_TRANSFER' WHERE type IS NULL; -- остальным ставим дефолт, поправим вручную если надо

ALTER TABLE transfers ALTER COLUMN type SET NOT NULL;