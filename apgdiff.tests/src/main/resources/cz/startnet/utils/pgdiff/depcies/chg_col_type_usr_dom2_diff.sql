SET search_path = public, pg_catalog;

CREATE DOMAIN dom2 AS integer
	CONSTRAINT chechk1 CHECK ((VALUE > 0));

ALTER DOMAIN dom2 OWNER TO botov_av;
