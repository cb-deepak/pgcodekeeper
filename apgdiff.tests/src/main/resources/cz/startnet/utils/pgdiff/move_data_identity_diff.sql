SET search_path = pg_catalog;

-- DEPCY: This VIEW depends on the TABLE: public.tbl

DROP VIEW public.v;

-- DEPCY: This CONSTRAINT depends on the TABLE: public.tbl

ALTER TABLE public.tbl
	DROP CONSTRAINT tbl_name22_check;

-- DEPCY: This CONSTRAINT depends on the TABLE: public.tbl

ALTER TABLE public.tbl
	DROP CONSTRAINT tbl_pkey;

ALTER TABLE public.tbl RENAME TO tbl_randomly_generated_part;

ALTER SEQUENCE public.tbl_did_seq RENAME TO tbl_did_seq_randomly_generated_part;

ALTER SEQUENCE public.tbl_did_2_seq RENAME TO tbl_did_2_seq_randomly_generated_part;

CREATE TABLE public.tbl (
	did integer NOT NULL,
	did_2 integer NOT NULL,
	name22 character varying(40) NOT NULL,
	event_time timestamp without time zone DEFAULT now() NOT NULL,
	description integer DEFAULT 55777,
	calculated bigint GENERATED ALWAYS AS ((did + 2000)) STORED
);

ALTER TABLE public.tbl ALTER COLUMN did ADD GENERATED BY DEFAULT AS IDENTITY (
	SEQUENCE NAME public.tbl_did_seq
	START WITH 1
	INCREMENT BY 1
	NO MAXVALUE
	NO MINVALUE
	CACHE 1
);

ALTER TABLE public.tbl ALTER COLUMN did_2 ADD GENERATED BY DEFAULT AS IDENTITY (
	SEQUENCE NAME public.tbl_did_2_seq
	START WITH 1
	INCREMENT BY 1
	NO MAXVALUE
	NO MINVALUE
	CACHE 1
);

CREATE VIEW public.v AS
	SELECT tbl.name22,
    tbl.description,
    1 AS qwerty
   FROM public.tbl;

ALTER TABLE public.tbl
	ADD CONSTRAINT tbl_name22_check CHECK (((name22)::text <> ''::text));

ALTER TABLE public.tbl
	ADD CONSTRAINT tbl_pkey PRIMARY KEY (did);

INSERT INTO public.tbl(did, did_2, name22, description, event_time) SELECT did, did_2, name22, description, event_time FROM public.tbl_randomly_generated_part;

DO $$ DECLARE public_tbl_did_restart_value integer = (SELECT nextval(pg_get_serial_sequence('public.tbl_randomly_generated_part', 'did')));
BEGIN
	EXECUTE 'ALTER TABLE public.tbl ALTER COLUMN did RESTART WITH ' || public_tbl_did_restart_value || ';';
END
$$;

DO $$ DECLARE public_tbl_did_2_restart_value integer = (SELECT nextval(pg_get_serial_sequence('public.tbl_randomly_generated_part', 'did_2')));
BEGIN
	EXECUTE 'ALTER TABLE public.tbl ALTER COLUMN did_2 RESTART WITH ' || public_tbl_did_2_restart_value || ';';
END
$$;

DROP TABLE public.tbl_randomly_generated_part;
