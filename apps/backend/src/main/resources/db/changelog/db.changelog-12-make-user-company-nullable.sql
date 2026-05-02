-- liquibase formatted sql

-- changeset hawa:12-make-user-company-nullable
-- comment: Admins are platform-level operators and do not belong to a tenant company. Drop NOT NULL on user.company_id and replace it with a CHECK constraint that allows NULL only for ADMIN. Migrate existing admin rows to NULL and remove the seeded "Hawa" placeholder company if no rows still reference it.
ALTER TABLE "user" ALTER COLUMN company_id DROP NOT NULL;

ALTER TABLE "user"
    ADD CONSTRAINT user_company_required_for_non_admin
    CHECK (role = 'ADMIN' OR company_id IS NOT NULL);

UPDATE "user" SET company_id = NULL WHERE role = 'ADMIN';

DELETE FROM company
    WHERE company_name = 'Hawa'
      AND NOT EXISTS (SELECT 1 FROM "user" WHERE company_id = company.company_id)
      AND NOT EXISTS (SELECT 1 FROM brand WHERE company_id = company.company_id);

-- rollback ALTER TABLE "user" DROP CONSTRAINT user_company_required_for_non_admin;
-- rollback ALTER TABLE "user" ALTER COLUMN company_id SET NOT NULL;
