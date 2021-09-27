/*
 * Copyright (c) 2018 Dafiti Group
 * 
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 * 
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * 
 */
package br.com.dafiti.parquet;

import br.com.dafiti.datalake.S3;
import br.com.dafiti.util.Statistics;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import org.apache.avro.Schema;
import org.apache.avro.Schema.Field;
import org.apache.avro.Schema.Type;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.generic.GenericRecordBuilder;
import org.apache.avro.Conversions;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.hadoop.fs.Path;
import org.apache.log4j.Logger;
import org.apache.parquet.avro.AvroParquetReader;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * This class read a csv file and write the records into a parquet file.
 *
 * @author Valdiney V GOMES
 */
public class CSVToParquet implements Runnable {

    private final HashMap<String, Type> typeMap;
    private final HashMap<String, String> logicalTypeMap;
    private final HashMap<String, Integer> decimalScaleMap;
    private final File inputFile;
    private final Schema schema;
    private final CompressionCodecName compression;
    private final Character delimiter;
    private final Character quote;
    private final Character quoteEscape;
    private final boolean replace;
    private final boolean header;
    private final int fieldKey;
    private final boolean duplicated;
    private final boolean merge;
    private final String bucketPath;
    private final String mode;
    private final boolean debug;

    /**
     * Constructor.
     *
     * @param inputFile CSV file.
     * @param compression Parquet file compression.
     * @param delimiter File delimiter.
     * @param quote File quote.
     * @param quoteEscape File escape.
     * @param schema Avro schema file.
     * @param header Identify if the file has header.
     * @param replace Identify if should replace the orignal file.
     * @param fieldKey Unique key field.
     * @param duplicated Identify if duplicated is allowed
     * @param merge Identify if should merge existing files.
     * @param bucket Identify storage bucket.
     * @param mode Identify partition mode.
     * @param debug Identify if should show detailed log message.
     */
    public CSVToParquet(
            File inputFile,
            String compression,
            Character delimiter,
            Character quote,
            Character quoteEscape,
            Schema schema,
            boolean header,
            boolean replace,
            int fieldKey,
            boolean duplicated,
            boolean merge,
            String bucket,
            String mode,
            boolean debug) {

        this.typeMap = new HashMap();
        this.logicalTypeMap = new HashMap();
        this.decimalScaleMap = new HashMap();
        this.inputFile = inputFile;
        this.schema = schema;
        this.delimiter = delimiter;
        this.quote = quote;
        this.quoteEscape = quoteEscape;
        this.replace = replace;
        this.header = header;
        this.fieldKey = fieldKey;
        this.duplicated = duplicated;
        this.merge = merge;
        this.bucketPath = bucket;
        this.mode = mode;
        this.debug = debug;

        switch (compression) {
            case "gzip":
                this.compression = CompressionCodecName.GZIP;
                break;
            case "snappy":
                this.compression = CompressionCodecName.SNAPPY;
                break;
            case "lzo":
                this.compression = CompressionCodecName.LZO;
                break;
            case "uncompressed":
                this.compression = CompressionCodecName.UNCOMPRESSED;
                break;
            default:
                this.compression = CompressionCodecName.GZIP;
                break;
        }
    }

    /**
     * Get the field type from cache to improve conversion performance.
     *
     * @param field Schema field
     * @return field type
     */
    private Type getType(Field field) {
        String key = field.name();
        Type type = field.schema().getType();

        if (typeMap.containsKey(key)) {
            type = typeMap.get(key);
        } else {
            if (type.equals(Type.UNION)) {
                for (Schema unionSchema : field.schema().getTypes()) {
                    if (!unionSchema.getType().equals(Type.NULL)) {
                        type = unionSchema.getType();

                        break;
                    }
                }
            }

            typeMap.put(key, type);
        }

        return type;
    }

    /**
     * Get the logical field type from cache to improve conversion performance.
     *
     * @param field Schema field
     * @return logical field type
     */
    private String getLogicalType(Field field) {
        String logicalType = new String();
        String key = field.name();

        if (logicalTypeMap.containsKey(key)) {
            logicalType = logicalTypeMap.get(key);
        } else {
            Type type = field.schema().getType();

            if (type.equals(Type.UNION)) {
                for (Schema unionSchema : field.schema().getTypes()) {
                    if (!unionSchema.getType().equals(Type.NULL)) {
                        logicalType = unionSchema.getProp("logicalType");

                        break;
                    }
                }
            } else {
                logicalType = field.schema().getProp("logicalType");
            }

            logicalTypeMap.put(key, logicalType);
        }

        return logicalType;
    }

    /**
     * Get the decimal field scale from cache to improve conversion performance.
     *
     * @param field Schema field.
     * @return decimal field scale.
     */
    private int getDecimalScale(Field field) {
        String key = field.name();
        int scale = 0;

        if (decimalScaleMap.containsKey(key)) {
            scale = decimalScaleMap.get(key);
        } else {
            Type type = field.schema().getType();

            if (type.equals(Type.UNION)) {
                for (Schema unionSchema : field.schema().getTypes()) {
                    if (!unionSchema.getType().equals(Type.NULL)) {
                        String logicalType = unionSchema.getProp("logicalType");

                        if (logicalType != null) {
                            if (logicalType.equals("decimal")) {
                                scale = Integer.valueOf(unionSchema.getObjectProp("scale").toString());
                            }
                        }

                        break;
                    }
                }
            } else {
                scale = Integer.valueOf(field.schema().getObjectProp("scale").toString());
            }

            decimalScaleMap.put(key, scale);
        }

        return scale;
    }

    /**
     * Convert a csv file to a parquet file based on determined schema.
     */
    @Override
    public void run() {
        //Log the process init.
        Logger.getLogger(this.getClass()).info("Converting CSV to Parquet: " + inputFile.getAbsolutePath());

        //Identifies the file name pattern. 
        String path = FilenameUtils.getFullPath(inputFile.getAbsolutePath());
        String name = FilenameUtils.getBaseName(inputFile.getName());

        //Get the parquet and crc file.            
        File originalFile = new File(path + name + ".original");
        File transientFile = new File(path + name + ".transient");
        File parquetFile = new File(path + name + "." + this.compression.toString().toLowerCase() + ".parquet");

        try {
            //Unique key list.
            HashSet<String> key = new HashSet();

            //Defines a statistics object;
            Statistics statistics = new Statistics();

            //Remove old files. 
            Files.deleteIfExists(originalFile.toPath());
            Files.deleteIfExists(transientFile.toPath());
            Files.deleteIfExists(parquetFile.toPath());

            //Enable decimal conversion support.
            GenericData genericData = new GenericData();
            genericData.addLogicalTypeConversion(new Conversions.DecimalConversion());

            //Convert to parquet.
            try (ParquetWriter<GenericRecord> parquetWriter = AvroParquetWriter.<GenericRecord>builder(new Path(transientFile.getAbsolutePath()))
                    .withSchema(schema)
                    .withDataModel(genericData)
                    .withCompressionCodec(this.compression)
                    .withDictionaryEncoding(true)
                    .build()) {

                //Convert to parquet.
                if (inputFile.isDirectory()) {
                    File[] files = inputFile.listFiles();

                    for (File file : files) {
                        this.toParquet(file, parquetWriter, key, statistics);
                    }
                } else {
                    this.toParquet(inputFile, parquetWriter, key, statistics);
                }

                //Identifies if merge is necessary.
                if (merge) {
                    String object = parquetFile.getName();

                    //Download the original object.
                    new S3().downloadObject(bucketPath, object, mode, originalFile);

                    //Identify if the original file was downloaded.
                    if (originalFile.exists()) {
                        GenericRecordBuilder builder = new GenericRecordBuilder(schema);

                        //Identifies parquet fields.
                        List<Field> fields = schema.getFields();

                        //Define the reader.
                        ParquetReader<GenericRecord> parquetReader = AvroParquetReader.<GenericRecord>builder(new Path(originalFile.getAbsolutePath()))
                                .withDataModel(genericData)
                                .disableCompatibility()
                                .build();

                        //Parquet row.
                        GenericRecord row;

                        //Read a parquet file.
                        while ((row = parquetReader.read()) != null) {
                            boolean add = true;

                            //Identify merge file records.
                            statistics.incrementOutputRows();

                            //Identify if can add a record.
                            if (fieldKey >= 0 && !duplicated) {
                                add = !key.contains(String.valueOf(row.get(fieldKey)));
                            }

                            if (add) {
                                for (Field field : fields) {
                                    //Reset the field.
                                    builder.clear(field);

                                    if (row.getSchema().getFields().contains(field)) {
                                        builder.set(field, row.get(field.name()));
                                    }
                                }

                                //Write date into parquet file.
                                parquetWriter.write(builder.build());
                            } else {
                                statistics.incrementOutputUpdatedRows();
                            }

                            //Identify the record being processed.
                            statistics.incrementRowNumber();
                        }
                    }
                }
            }

            //Identifies if the csv file is empty.
            if (statistics.getRowNumber() == 0) {
                throw new Exception("Empty csv file!");
            } else {
                //Print on console.
                System.out.println("[" + parquetFile.getName() + "] records: "
                        + statistics.getOutputRows()
                        + ", Delta: "
                        + (statistics.getInputRows() + statistics.getDuplicatedRows())
                        + ", ( Updated: " + statistics.getOutputUpdatedRows() + ", Inserted: " + (statistics.getInputRows() - statistics.getOutputUpdatedRows()) + ", Duplicated:" + statistics.getDuplicatedRows() + " )"
                        + " Final: "
                        + (statistics.getOutputRows() + (statistics.getInputRows() - statistics.getOutputUpdatedRows())));
            }

            //Rename transient to final file. 
            Files.move(transientFile.toPath(), parquetFile.toPath(), REPLACE_EXISTING);

            //Remove the original file.
            Files.deleteIfExists(originalFile.toPath());

            //Identify if should remove csv file. 
            if (replace) {
                if (inputFile.isDirectory()) {
                    FileUtils.deleteDirectory(inputFile);
                } else {

                    Files.deleteIfExists(inputFile.toPath());
                }
            }
        } catch (Exception ex) {
            Logger.getLogger(this.getClass()).error("Error [" + ex + "] generating parquet file " + parquetFile.getName());
            System.exit(1);
        }
    }

    /**
     * Convert a file to parquet.
     *
     * @param file CSV File.
     * @param parquetWriter Parquet Writer.
     * @param key Unique keys mapper.
     * @param statistics Statistics collector.
     * @throws Exception
     */
    private void toParquet(
            File file,
            ParquetWriter<GenericRecord> parquetWriter,
            HashSet<String> key,
            Statistics statistics) throws Exception {

        //Number of fields in the schema.
        int fieldCount;

        //Value being processed.
        String[] record;
        String value;

        //Define a settings.
        CsvParserSettings parserSettings = new CsvParserSettings();
        parserSettings.setNullValue("");
        parserSettings.setMaxCharsPerColumn(-1);

        //Define format settings
        parserSettings.getFormat().setDelimiter(delimiter);
        parserSettings.getFormat().setQuote(quote);
        parserSettings.getFormat().setQuoteEscape(quoteEscape);
        parserSettings.getFormat().setLineSeparator("\r\n");

        //Define the input buffer.
        parserSettings.setInputBufferSize(2 * (1024 * 1024));

        //Define a csv parser.
        CsvParser csvParser = new CsvParser(parserSettings);

        //Init a parser.
        csvParser.beginParsing(file);

        //Identifies parquet fields.
        List<Field> fields = schema.getFields();

        //Get parquet field count.
        fieldCount = schema.getFields().size();

        //Define a record builder.
        GenericRecordBuilder builder = new GenericRecordBuilder(schema);

        //Process each csv record.
        while ((record = csvParser.parseNext()) != null) {
            boolean add = false;

            //Identify if the csv the field count specified in the schema.                
            if (record.length < fieldCount) {
                throw new Exception(inputFile.getName() + " expected " + fieldCount + " fields, but received only " + record.length + " : " + String.join("|", record));
            }

            //Identify insert rule. 
            if (fieldKey < 0 || duplicated) {
                add = true;
            } else if (fieldKey >= 0 && !duplicated) {
                add = key.add(record[fieldKey]);
            }

            //Ignore the header.
            if (!(statistics.getRowNumber() == 0 && header)) {
                //Identify duplicated key.
                if (!add) {
                    if (debug) {
                        System.out.println("Duplicated key in file: [" + record[fieldKey] + "]");
                    }
                    statistics.incrementDuplicatedRows();
                } else {
                    statistics.incrementInputRows();

                    for (Field field : fields) {
                        value = "";

                        //Get field type.
                        Type type = this.getType(field);

                        //Get logical type.
                        String logicalType = this.getLogicalType(field);

                        //Get field value.
                        if ((record.length - 1) >= field.pos()) {
                            value = record[field.pos()];
                        }

                        //Identify if the field is empty.
                        if (value == null || value.isEmpty()) {
                            value = null;
                        }

                        //Reset the field.
                        builder.clear(field);

                        //Identify if the value is null.
                        if (value != null) {
                            try {
                                //Convert values to fit avro type.
                                switch (type) {
                                    case BOOLEAN:
                                        builder.set(field, BooleanUtils.toBoolean(value));
                                        break;
                                    case INT:
                                        if (logicalType == null) {
                                            builder.set(field, NumberUtils.toInt(value, 0));
                                        } else if (logicalType.equals("date")) {
                                            //A date logical type annotates an Avro int, where the int stores the number of days from the unix epoch.
                                            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");

                                            Date date = format.parse(value);
                                            Calendar calendar = Calendar.getInstance();
                                            calendar.setTime(date);

                                            Long timeInMillis = calendar.getTimeInMillis();
                                            Long timeZoneOffset = (long) calendar.getTimeZone().getOffset(calendar.getTimeInMillis());

                                            builder.set(field, timeInMillis + timeZoneOffset / (1000 * 60 * 60 * 24));
                                        }

                                        break;
                                    case LONG:
                                        if (logicalType == null) {
                                            builder.set(field, NumberUtils.toLong(value, 0));
                                        } else if (logicalType.equals("timestamp-millis")) {
                                            //A timestamp-millis logical type annotates an Avro long, where the long stores the number of milliseconds from the unix epoch.
                                            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");

                                            Date date = format.parse(value);
                                            Calendar calendar = Calendar.getInstance();
                                            calendar.setTime(date);

                                            Long timeInMillis = calendar.getTimeInMillis();
                                            Long timeZoneOffset = (long) calendar.getTimeZone().getOffset(calendar.getTimeInMillis());

                                            builder.set(field, timeInMillis + timeZoneOffset);
                                        }

                                        break;
                                    case FLOAT:
                                        builder.set(field, NumberUtils.toFloat(value, 0));
                                        break;
                                    case DOUBLE:
                                        builder.set(field, NumberUtils.toDouble(value, 0));
                                        break;
                                    case BYTES:
                                    case FIXED:
                                        if (logicalType == null) {
                                            builder.set(field, value.getBytes());
                                        } else if (logicalType.equals("decimal")) {
                                            //A decimal logical type annotates Avro bytes or fixed types.
                                            //The byte array must contain the two's-complement representation of the unscaled integer value in big-endian byte order.
                                            //The scale is fixed, and is specified using an attribute.
                                            int scale = this.getDecimalScale(field);

                                            builder.set(field, new BigDecimal(value).setScale(scale, BigDecimal.ROUND_UP));
                                        }

                                        break;
                                    case STRING:
                                        builder.set(field, value);
                                        break;
                                    default:
                                        break;
                                }
                            } catch (NumberFormatException | ParseException ex) {
                                throw new Exception(ex.getMessage() + " on record " + String.join("|", record) + " at field " + field.name() + " with value " + value);
                            }
                        }
                    }

                    //Write date into parquet file.
                    parquetWriter.write(builder.build());
                }
            }

            //Identify the record being processed.
            statistics.incrementRowNumber();
        }

        //Stop the parser.
        csvParser.stopParsing();
    }
}
