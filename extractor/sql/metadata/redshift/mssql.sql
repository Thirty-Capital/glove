declare @table_schema varchar(max) = coalesce(lower('${INPUT_TABLE_SCHEMA}'),char(39) + 'dbo' + char(39))
declare @table_name varchar(max) = lower('${INPUT_TABLE_NAME}')
declare @custom_primary_key nvarchar(max) = ''
declare @custom_primary_key_var nvarchar(max) = ''
SELECT @custom_primary_key_var = STUFF(
			(SELECT  '+''|''+'+ Column_Name  
              FROM 
			  ( 			  
			  	SELECT  'cast('+ccu.Column_Name+ ' as varchar)' as Column_Name
                FROM 
				    INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc, 
				    INFORMATION_SCHEMA.CONSTRAINT_COLUMN_USAGE ccu 
				WHERE 
				    ccu.Constraint_Name = tc.Constraint_Name
				    AND ccu.Table_Name = tc.Table_Name
				    AND tc.Constraint_Type = 'PRIMARY KEY'
				    AND LOWER(ccu.Table_Schema) = LOWER(@table_schema)
				    AND LOWER(ccu.Table_Name) = LOWER(@table_name)
			  )as a FOR XML PATH ('')
			 ), 1, 1, ''
		)
SELECT @custom_primary_key = coalesce(substring(@custom_primary_key_var,5,len(@custom_primary_key_var)),char(39)+char(39)+char(39)+char(39),char(39)+char(39)+char(39)+char(39))
SELECT * 
FROM (
	SELECT
		999 AS ordinal_position,
 		'convert(varchar,getdate(), 120)' + ' AS etl_load_date'  AS fields,
   	    'convert(varchar,getdate(), 120)' AS casting,
		'datetime' AS field_type,
		'' AS json,
		'etl_load_date' AS column_name,
		0 AS column_key

    UNION ALL
    
    SELECT DISTINCT
        0 AS ordinal_position,
        CASE WHEN '${CUSTOM_PRIMARY_KEY}'!= '' 
             THEN '${CUSTOM_PRIMARY_KEY}' + ' AS custom_primary_key' 
             ELSE @custom_primary_key + ' AS custom_primary_key'  
        end AS fields,
		CASE WHEN '${CUSTOM_PRIMARY_KEY}'!= '' 
             THEN '${CUSTOM_PRIMARY_KEY}' + ' AS custom_primary_key' 
             ELSE @custom_primary_key + ' AS custom_primary_key'    
        end AS casting,
        'varchar(255)' AS field_type,
		'' AS json,
        'custom_primary_key' AS column_name,
        1 AS column_key
        
	UNION ALL 
	 
		SELECT DISTINCT 
		ordinal_position,
		CASE
			WHEN data_type in ('datetime','datetime2','smalldatetime','	datetimeoffset') THEN  'convert(varchar,' + column_name + ', 120) as ' + column_name 
            WHEN data_type in ('date') THEN 'convert(varchar,' + column_name + ', 23) as ' + column_name
			WHEN data_type = 'bit' THEN 'CAST(' + column_name + ' as tinyint) as ' + column_name
            WHEN data_type = 'image' THEN '''' + 'image' + '''' + ' as ' + column_name
			ELSE column_name		
		END	AS fields,
		CASE
			WHEN data_type in ('datetime','datetime2','smalldatetime','	datetimeoffset') THEN 'convert(varchar,' + column_name + ', 120)' 	
            WHEN data_type in ('date') THEN 'convert(varchar,' + column_name + ', 23)'
			WHEN data_type = 'bit' THEN 'CAST(' + column_name + ' as tinyint)'
            WHEN data_type = 'image' THEN '''' + 'image' + ''''
			ELSE column_name		
		END	AS casting,
		CASE
            WHEN data_type in ('rowversion','varchar', 'char', 'varbinary', 'binary', 'nvarchar', 'nchar')AND CHARACTER_MAXIMUM_LENGTH <= 0    THEN 'varchar(max)'
			WHEN data_type in ('rowversion','varchar', 'char', 'varbinary', 'binary', 'nvarchar', 'nchar')AND ABS(CHARACTER_MAXIMUM_LENGTH) * 2 >= 8000 THEN 'varchar(max)'
            WHEN data_type in ('rowversion','varchar', 'char', 'varbinary', 'binary', 'nvarchar', 'nchar')AND ABS(CHARACTER_MAXIMUM_LENGTH) * 2 BETWEEN 1 AND 7999 THEN 'varchar' + '(' + CAST(ABS(CHARACTER_MAXIMUM_LENGTH) * 2 AS VARCHAR) + ')'
			WHEN data_type in ('numeric') THEN  'numeric' + '(' + CAST(ABS(NUMERIC_PRECISION)AS VARCHAR) + ',' + CAST(ABS(NUMERIC_SCALE)AS VARCHAR) + ')'
			WHEN data_type in ('money') THEN  'numeric(15,4)'
			WHEN data_type in ('smallmoney') THEN  'numeric(6,4)'
			WHEN data_type in ('decimal') THEN  'decimal' + '(' + CAST(ABS(NUMERIC_PRECISION)AS VARCHAR) + ',' + CAST(ABS(NUMERIC_SCALE)AS VARCHAR) + ')'
			WHEN data_type in ('float') THEN   'float' 
			WHEN data_type in ('double precision') THEN   'float' 
			WHEN data_type = 'bit' THEN 'tinyint'
            WHEN data_type = 'image' THEN  'varchar(5)'
			WHEN data_type in ('int','integer') THEN  'int'
			WHEN data_type in ('smallint','tinyint') THEN  'smallint'
            WHEN data_type in ('bigint') THEN  'bigint'			
			WHEN data_type in ('datetime','datetime2','smalldatetime','	datetimeoffset') THEN 'varchar(19)' 	
            WHEN data_type in ('text','xml','xml2') THEN  'varchar(max)'						
			ELSE data_type
		END AS field_type ,
		'' AS json,
		LOWER(column_name) AS column_name,

		0 AS column_key
	FROM
		information_schema.columns c
	WHERE
		LOWER(c.table_schema) = LOWER(@table_schema)
		AND 
		LOWER(c.table_name) = LOWER(@table_name)
) x
ORDER BY x.ordinal_position
