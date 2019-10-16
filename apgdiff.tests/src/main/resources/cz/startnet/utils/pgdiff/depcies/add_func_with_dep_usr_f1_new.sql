CREATE OR REPLACE FUNCTION public.f1(s integer, k integer = 43) RETURNS integer
    LANGUAGE plpgsql
    AS $$ begin SELECT count(t.c1) from public.t1 t where c1 = s ; end $$;