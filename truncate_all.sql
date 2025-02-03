DECLARE
    r RECORD;
BEGIN
    -- Loop through all tables and truncate them
    FOR r IN (SELECT tablename FROM pg_tables WHERE schemaname = 'public')
    LOOP
        EXECUTE 'TRUNCATE TABLE public.' || r.tablename || ' CASCADE';
    END LOOP;
END $$