SET search_path = pg_catalog;

ALTER FOREIGN DATA WRAPPER test_fdw_1 HANDLER handler_function_1;

ALTER FOREIGN DATA WRAPPER test_fdw_2 VALIDATOR fdw_test_validator_1;

ALTER FOREIGN DATA WRAPPER test_fdw_0 OPTIONS (ADD debug 'true');

ALTER FOREIGN DATA WRAPPER test_fdw_3 OPTIONS (DROP host );

ALTER FOREIGN DATA WRAPPER test_fdw_3 OPTIONS (SET dbname 'newdb');

ALTER FOREIGN DATA WRAPPER test_fdw_3 OPTIONS (ADD port '5432');

ALTER FOREIGN DATA WRAPPER test_fdw_4 OWNER TO khazieva_gr;
