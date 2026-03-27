-- liquibase formatted sql

-- changeset hawa:1-create-schema
-- comment: Initial schema — enum types and all tables

-- Enum types
CREATE TYPE user_role AS ENUM ('ADMIN', 'MARKETING_USER');
CREATE TYPE keyword_type AS ENUM ('BRAND_NAME', 'PRODUCT', 'HASHTAG');
CREATE TYPE language AS ENUM ('EN', 'AR');
CREATE TYPE data_source AS ENUM ('REDDIT', 'CSV_UPLOAD');
CREATE TYPE report_status AS ENUM ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED');
CREATE TYPE emotion AS ENUM ('JOY', 'ANGER', 'SADNESS', 'FEAR', 'SURPRISE', 'DISGUST');
CREATE TYPE aspect AS ENUM ('PRODUCT', 'SERVICE', 'DELIVERY', 'PRICING');

-- company
CREATE TABLE company (
    company_id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_name VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP NOT NULL DEFAULT now()
);

-- "user" is a reserved word in PostgreSQL, so we quote it
CREATE TABLE "user" (
    user_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id  BIGINT NOT NULL REFERENCES company(company_id),
    first_name  VARCHAR(255) NOT NULL,
    last_name   VARCHAR(255) NOT NULL,
    role        user_role NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_user_company_id ON "user"(company_id);

-- brand
CREATE TABLE brand (
    brand_id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    brand_name       VARCHAR(255) NOT NULL,
    company_id       BIGINT NOT NULL REFERENCES company(company_id),
    industry         VARCHAR(255),
    status_indicator NUMERIC,
    created_at       TIMESTAMP NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_brand_company_id ON brand(company_id);

-- keyword
CREATE TABLE keyword (
    keyword_id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    brand_id     BIGINT NOT NULL REFERENCES brand(brand_id),
    keyword      TEXT NOT NULL,
    keyword_type keyword_type NOT NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_keyword_brand_id ON keyword(brand_id);

-- report
CREATE TABLE report (
    report_id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES "user"(user_id),
    brand_id    BIGINT NOT NULL REFERENCES brand(brand_id),
    score       INTEGER,
    summary     TEXT,
    data_source data_source NOT NULL,
    status      report_status NOT NULL DEFAULT 'PENDING',
    date_from   DATE,
    date_to     DATE,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    finished_at TIMESTAMP
);

CREATE INDEX idx_report_user_id  ON report(user_id);
CREATE INDEX idx_report_brand_id ON report(brand_id);

-- post
CREATE TABLE post (
    post_id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    report_id BIGINT NOT NULL REFERENCES report(report_id),
    post_text TEXT NOT NULL,
    post_url  TEXT,
    language  language NOT NULL
);

CREATE INDEX idx_post_report_id ON post(report_id);

-- review (one-to-one with post)
CREATE TABLE review (
    review_id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    post_id    BIGINT NOT NULL UNIQUE REFERENCES post(post_id),
    llm_score  NUMERIC,
    score      NUMERIC NOT NULL,
    emotion    emotion,
    aspect     aspect NOT NULL,
    confidence NUMERIC NOT NULL
);

-- feedback
CREATE TABLE feedback (
    feedback_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    review_id   BIGINT NOT NULL REFERENCES review(review_id),
    user_id     BIGINT NOT NULL REFERENCES "user"(user_id),
    brief       TEXT NOT NULL
);

CREATE INDEX idx_feedback_review_id ON feedback(review_id);
CREATE INDEX idx_feedback_user_id   ON feedback(user_id);

-- rollback DROP TABLE IF EXISTS feedback CASCADE;
-- rollback DROP TABLE IF EXISTS review CASCADE;
-- rollback DROP TABLE IF EXISTS post CASCADE;
-- rollback DROP TABLE IF EXISTS report CASCADE;
-- rollback DROP TABLE IF EXISTS keyword CASCADE;
-- rollback DROP TABLE IF EXISTS brand CASCADE;
-- rollback DROP TABLE IF EXISTS "user" CASCADE;
-- rollback DROP TABLE IF EXISTS company CASCADE;
-- rollback DROP TYPE IF EXISTS aspect;
-- rollback DROP TYPE IF EXISTS emotion;
-- rollback DROP TYPE IF EXISTS report_status;
-- rollback DROP TYPE IF EXISTS data_source;
-- rollback DROP TYPE IF EXISTS language;
-- rollback DROP TYPE IF EXISTS keyword_type;
-- rollback DROP TYPE IF EXISTS user_role;
