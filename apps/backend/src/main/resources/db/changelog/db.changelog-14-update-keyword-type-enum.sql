-- liquibase formatted sql

-- changeset hawa:14-update-keyword-type-enum splitStatements:false
-- comment: Replace HASHTAG with MISSPELLING + OTHER on keyword_type. Existing HASHTAG rows are mapped to OTHER.
ALTER TYPE keyword_type RENAME TO keyword_type_old;

CREATE TYPE keyword_type AS ENUM ('BRAND_NAME', 'PRODUCT', 'MISSPELLING', 'OTHER');

ALTER TABLE keyword
    ALTER COLUMN keyword_type TYPE keyword_type
    USING (
        CASE keyword_type::text
            WHEN 'HASHTAG' THEN 'OTHER'
            ELSE keyword_type::text
        END
    )::keyword_type;

DROP TYPE keyword_type_old;

-- rollback ALTER TYPE keyword_type RENAME TO keyword_type_new;
-- rollback CREATE TYPE keyword_type AS ENUM ('BRAND_NAME', 'PRODUCT', 'HASHTAG');
-- rollback ALTER TABLE keyword ALTER COLUMN keyword_type TYPE keyword_type USING (CASE keyword_type::text WHEN 'MISSPELLING' THEN 'BRAND_NAME' WHEN 'OTHER' THEN 'BRAND_NAME' ELSE keyword_type::text END)::keyword_type;
-- rollback DROP TYPE keyword_type_new;
