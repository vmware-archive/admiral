-- template for creating a table per document type
-- required variables:
--   $tableName - the name of the document table to create

CREATE TABLE IF NOT EXISTS $tableName
(
    data jsonb,
    documentauthprincipallink text COLLATE pg_catalog."default",
    documentkind text COLLATE pg_catalog."default",
    documenttransactionid text COLLATE pg_catalog."default",
    documentupdatetimemicros bigint,
    documentversion bigint NOT NULL,
    documentselflink text COLLATE pg_catalog."default" NOT NULL,
    documentexpirationtimemicros bigint,
    documentupdateaction text COLLATE pg_catalog."default",
    CONSTRAINT $tableName_pkey PRIMARY KEY (documentselflink)
)
WITH (
    OIDS = FALSE
)
