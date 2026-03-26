# Database Schema

> **Reference only** — not executable SQL. See migrations for the source of truth.

## Entity Relationship Summary

```
Company 1──* User
Company 1──* Brand
Brand   1──* Keyword
Brand   1──* Report
User    1──* Report
User    1──* Feedback
Report  1──* Post
Post    1──1 Review
Review  1──* Feedback
```

## Tables

### company

| Column | Type | Constraints | Default |
|--------|------|-------------|---------|
| `company_id` | `bigint` | PK, GENERATED ALWAYS AS IDENTITY | — |
| `company_name` | `varchar` | NOT NULL | — |
| `created_at` | `timestamp` | NOT NULL | `now()` |
| `updated_at` | `timestamp` | NOT NULL | `now()` |

### user

| Column | Type | Constraints | Default |
|--------|------|-------------|---------|
| `user_id` | `bigint` | PK, GENERATED ALWAYS AS IDENTITY | — |
| `company_id` | `bigint` | FK → `company.company_id`, NOT NULL | — |
| `first_name` | `varchar` | NOT NULL | — |
| `last_name` | `varchar` | NOT NULL | — |
| `role` | `enum` | NOT NULL | — |
| `email` | `varchar` | NOT NULL, UNIQUE | — |
| `password` | `varchar` | NOT NULL | — |
| `created_at` | `timestamp` | NOT NULL | `now()` |
| `updated_at` | `timestamp` | NOT NULL | `now()` |

### brand

| Column | Type | Constraints | Default |
|--------|------|-------------|---------|
| `brand_id` | `bigint` | PK, GENERATED ALWAYS AS IDENTITY | — |
| `brand_name` | `varchar` | NOT NULL | — |
| `company_id` | `bigint` | FK → `company.company_id`, NOT NULL | — |
| `industry` | `varchar` | — | — |
| `status_indicator` | `numeric` | — | — |
| `created_at` | `timestamp` | NOT NULL | `now()` |
| `updated_at` | `timestamp` | NOT NULL | `now()` |

### keyword

| Column | Type | Constraints | Default |
|--------|------|-------------|---------|
| `keyword_id` | `bigint` | PK, GENERATED ALWAYS AS IDENTITY | — |
| `brand_id` | `bigint` | FK → `brand.brand_id`, NOT NULL | — |
| `keyword` | `text` | NOT NULL | — |
| `keyword_type` | `enum` | NOT NULL | — |
| `created_at` | `timestamp` | NOT NULL | `now()` |
| `updated_at` | `timestamp` | NOT NULL | `now()` |

### report

| Column | Type | Constraints | Default |
|--------|------|-------------|---------|
| `report_id` | `bigint` | PK, GENERATED ALWAYS AS IDENTITY | — |
| `user_id` | `bigint` | FK → `user.user_id`, NOT NULL | — |
| `brand_id` | `bigint` | FK → `brand.brand_id`, NOT NULL | — |
| `score` | `integer` | — | — |
| `summary` | `text` | — | — |
| `data_source` | `enum` | NOT NULL | — |
| `status` | `enum` | NOT NULL | `'PENDING'` |
| `date_from` | `date` | — | — |
| `date_to` | `date` | — | — |
| `created_at` | `timestamp` | NOT NULL | `now()` |
| `finished_at` | `timestamp` | — | — |

### post

| Column | Type | Constraints | Default |
|--------|------|-------------|---------|
| `post_id` | `bigint` | PK, GENERATED ALWAYS AS IDENTITY | — |
| `report_id` | `bigint` | FK → `report.report_id`, NOT NULL | — |
| `post_text` | `text` | NOT NULL | — |
| `post_url` | `text` | — | — |
| `language` | `enum` | NOT NULL | — |

### review

| Column | Type | Constraints | Default |
|--------|------|-------------|---------|
| `review_id` | `bigint` | PK, GENERATED ALWAYS AS IDENTITY | — |
| `post_id` | `bigint` | FK → `post.post_id`, UNIQUE, NOT NULL | — |
| `llm_score` | `numeric` | — | — |
| `score` | `numeric` | NOT NULL | — |
| `emotion` | `enum` | — | — |
| `aspect` | `enum` | NOT NULL | — |
| `confidence` | `numeric` | NOT NULL | — |

### feedback

| Column | Type | Constraints | Default |
|--------|------|-------------|---------|
| `feedback_id` | `bigint` | PK, GENERATED ALWAYS AS IDENTITY | — |
| `review_id` | `bigint` | FK → `review.review_id`, NOT NULL | — |
| `user_id` | `bigint` | FK → `user.user_id`, NOT NULL | — |
| `brief` | `text` | NOT NULL | — |

## Enum Types

These columns use PostgreSQL custom enum types (`USER-DEFINED` in the dump):

| Column | Table | Known/Expected Values |
|--------|-------|----------------------|
| `role` | `user` | _(e.g., ADMIN, MARKETING_USER)_ |
| `keyword_type` | `keyword` | _(e.g., BRAND_NAME, PRODUCT, HASHTAG)_ |
| `language` | `post` | _(e.g., EN, AR)_ |
| `data_source` | `report` | _(e.g., REDDIT, CSV_UPLOAD)_ |
| `status` | `report` | `PENDING`, _(e.g., PROCESSING, COMPLETED, FAILED)_ |
| `emotion` | `review` | _(e.g., JOY, ANGER, SADNESS, FEAR, SURPRISE, DISGUST)_ |
| `aspect` | `review` | _(e.g., PRODUCT, SERVICE, DELIVERY, PRICING)_ |

> **Note:** Exact enum values should be confirmed against the database or migration files. Values listed as _(e.g., ...)_ are inferred from the project context.
