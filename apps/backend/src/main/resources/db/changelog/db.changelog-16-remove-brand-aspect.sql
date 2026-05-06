-- liquibase formatted sql

-- changeset hawa:16-remove-brand-aspect
-- comment: Remove BRAND from the aspect enum; reverts changeset 13. Postgres cannot drop an enum value in place, so rename-and-recreate.
UPDATE review SET aspect = 'PRODUCT' WHERE aspect = 'BRAND';

ALTER TYPE aspect RENAME TO aspect_old;
CREATE TYPE aspect AS ENUM ('PRODUCT', 'SERVICE', 'DELIVERY', 'PRICING');
ALTER TABLE review ALTER COLUMN aspect TYPE aspect USING aspect::text::aspect;
DROP TYPE aspect_old;

-- rollback not supported: re-adding BRAND would require coordinating with application code
