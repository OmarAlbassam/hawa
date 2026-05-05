-- liquibase formatted sql

-- changeset hawa:15-keyword-brand-cascade
-- comment: Cascade deletes from brand to its keywords so removing a brand cleans up its keyword rows
ALTER TABLE keyword DROP CONSTRAINT keyword_brand_id_fkey;
ALTER TABLE keyword ADD CONSTRAINT keyword_brand_id_fkey
    FOREIGN KEY (brand_id) REFERENCES brand(brand_id) ON DELETE CASCADE;

-- rollback ALTER TABLE keyword DROP CONSTRAINT keyword_brand_id_fkey;
-- rollback ALTER TABLE keyword ADD CONSTRAINT keyword_brand_id_fkey FOREIGN KEY (brand_id) REFERENCES brand(brand_id);
