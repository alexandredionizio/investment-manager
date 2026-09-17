ALTER TABLE incomes
    ADD COLUMN base_date DATE;

UPDATE incomes
SET base_date = payment_date
WHERE base_date IS NULL;

ALTER TABLE incomes
    ALTER COLUMN base_date SET NOT NULL;