SELECT * FROM (

    SELECT DISTINCT
        0 AS ordinal_position,
        CASE WHEN '${CUSTOM_PRIMARY_KEY}' != '' THEN '${CUSTOM_PRIMARY_KEY}' || ' AS custom_primary_key' ELSE  '1' || ' as custom_primary_key'  END::varchar(255) AS fields,
		CASE WHEN '${CUSTOM_PRIMARY_KEY}' != '' THEN '${CUSTOM_PRIMARY_KEY}' ELSE '1' END::varchar(255) AS casting,
		''																AS field_type,
		'{"name": "custom_primary_key","type":"STRING"}'::varchar(255) 	AS json,
        'custom_primary_key'::varchar(50) 								AS column_name,
        1 																AS column_key,
		''                                                              AS encoding

	UNION ALL

    SELECT DISTINCT
        ordinal_position,
        CASE
            WHEN data_type = 'date' THEN 'TO_CHAR ( ' || column_name || ' , ' || chr(39) || 'YYYY-MM-DD' ||  chr(39) || ') as ' || column_name
            WHEN data_type IN ( 'timestamp with time zone', 'timestamp without time zone' ) THEN 'TO_CHAR ( ' || column_name || ' , ' || chr(39) || 'YYYY-MM-DD HH24:MI:SS ' || '${TIMEZONE_OFFSET}' || chr(39) || ') as ' || column_name
            WHEN data_type IN ( '"char"', 'character varying', 'text' ) then  column_name || ' as ' || column_name
            ELSE column_name
        END::varchar(255) AS fields,
        CASE
            WHEN data_type = 'date' THEN 'TO_CHAR ( ' || column_name || ' , ' || chr(39) || 'YYYY-MM-DD' ||  chr(39) || ')'
            WHEN data_type IN ( 'timestamp with time zone', 'timestamp without time zone' ) THEN 'TO_CHAR ( ' || column_name || ' , ' || chr(39) || 'YYYY-MM-DD HH24:MI:SS ' || '${TIMEZONE_OFFSET}' || chr(39) || ')'
            WHEN data_type IN ( '"char"', 'character varying', 'text' ) then  column_name
            ELSE column_name
        END::varchar(255) AS casting,
        '' AS field_type,
        ( '{"name": "' || column_name ||  '","type":' ||
            CASE
                WHEN data_type IN ( 'smallint', 'integer', 'bigint' ) 																THEN '"INTEGER"'
				WHEN data_type IN ( 'real', 'double precision', 'numeric' ) 														THEN '"FLOAT"'
                WHEN data_type IN ( '"char"', 'character varying', 'text' ) 														THEN '"STRING"'                
                WHEN data_type IN ( 'timestamp with time zone', 'timestamp without time zone', 'time without time zone' ) 			THEN '"TIMESTAMP"'
				WHEN data_type IN ( 'date' ) 																						THEN '"DATE"'				
                WHEN data_type = 'boolean' 																							THEN '"BOOLEAN"'
            END::varchar(255) ||' }' ) 						AS json,
        lower( column_name ) 								AS column_name,
        0 													AS column_key,
		''                                                  AS encoding
    FROM
		information_schema.columns c
   	WHERE 1=1
    AND LOWER( c.table_schema ) = lower('${INPUT_TABLE_SCHEMA}')
   	AND LOWER( c.table_name ) = lower('${INPUT_TABLE_NAME}')
	AND UPPER(c.column_name) NOT IN (${METADATA_BLACKLIST})
    
    UNION ALL

    SELECT
        998 AS ordinal_position,
        ( 'to_char( SYSDATE,' || chr(39) || 'YYYY-MM-DD HH24:MI:SS ' || '${TIMEZONE_OFFSET}' || chr(39) || ') as etl_load_date' )::varchar(255) AS fields,
		( 'to_char( SYSDATE,' || chr(39) || 'YYYY-MM-DD HH24:MI:SS ' || '${TIMEZONE_OFFSET}' || chr(39) || ')' )::varchar(255) AS casting,
        ''						 									AS field_type,
  		'{"name": "etl_load_date","type":"STRING"}'::varchar(255) 	AS json,
        'etl_load_date'::varchar(50) 								AS column_name,
        0 															AS column_key,
		''                                                          AS encoding
) x
ORDER BY x.ordinal_position
