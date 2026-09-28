ALTER TABLE app_meta.field_definition
    ADD COLUMN business_key BOOLEAN NOT NULL DEFAULT FALSE;

WITH ranked_candidates AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY template_version_id
               ORDER BY display_order, id
           ) AS candidate_order
    FROM app_meta.field_definition
    WHERE required = TRUE
      AND unique_value = TRUE
      AND indexed = TRUE
      AND searchable = TRUE
)
UPDATE app_meta.field_definition field
SET business_key = TRUE
FROM ranked_candidates candidate
WHERE field.id = candidate.id
  AND candidate.candidate_order = 1;

CREATE UNIQUE INDEX uq_field_business_key_per_version
    ON app_meta.field_definition(template_version_id)
    WHERE business_key = TRUE;
