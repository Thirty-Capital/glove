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
package br.com.dafiti.splitter;

import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import com.univocity.parsers.csv.CsvWriter;
import com.univocity.parsers.csv.CsvWriterSettings;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.UUID;
import java.util.logging.Level;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.apache.log4j.Logger;

/**
 * This class reads CSV file and writes to CSV file(s) per partition.
 *
 * @author Valdiney V GOMES
 */
public class CSVSplitter implements Runnable {

    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(CSVSplitter.class.getName());

    private final File csvFile;
    private final int partitionColumn;
    private final Character delimiter;
    private final Character quote;
    private final Character quoteEscape;
    private final boolean header;
    private final boolean replace;
    private final boolean readable;
    private final String splitStrategy;

    /**
     * Constructor.
     *
     * @param csvFile Csv File.
     * @param fieldPartition Partition field.
     * @param delimiter File delimiter.
     * @param quote File quote.
     * @param quoteEscape File escape.
     * @param header Identify if the file has header.
     * @param replace Identify if should replace the orignal file.
     * @param readable Identifies if partition name should be readable at runtime.
     * @param splitStrategy Identify if should use the fastest strategy to partitioning.
     */
    public CSVSplitter(
            File csvFile,
            int fieldPartition,
            Character delimiter,
            Character quote,
            Character quoteEscape,
            boolean header,
            boolean replace,
            boolean readable,
            String splitStrategy) {

        this.csvFile = csvFile;
        this.partitionColumn = fieldPartition;
        this.delimiter = delimiter;
        this.quote = quote;
        this.quoteEscape = quoteEscape;
        this.replace = replace;
        this.header = header;
        this.readable = readable;
        this.splitStrategy = splitStrategy;

    }

    /**
     * CSV file splitter into file(s) by partition.
     */
    @Override
    public void run() {
        try {
            if (splitStrategy.equalsIgnoreCase("FAST")) {
                this.fastSplit();
            } else {
                this.secureSplit();
            }

            //Identify if should remove csv file. 
            if (replace) {
                csvFile.delete();
            }
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "Error [" + ex + "] splitting CSV", ex);
            System.exit(1);
        }
    }

    /**
     * Fast split mode for trusted files.
     *
     * @throws IOException
     */
    private void fastSplit() throws IOException {
        String part = "";
        String fileHeader = "";
        int lineNumber = 0;
        HashMap<String, BufferedWriter> partitions = new HashMap<>();

        LOG.info("Splitting CSV in fast mode");

        try (LineIterator lineIterator = FileUtils.lineIterator(csvFile, "UTF-8")) {
            while (lineIterator.hasNext()) {
                String line = lineIterator.nextLine();

                if (!(lineNumber == 0 && this.header)) {
                    String[] split = line.split(delimiter.toString());

                    if (split.length != 0) {
                        if (split[split.length - 1].startsWith("\"") && !split[split.length - 1].endsWith("\"")) {
                            part = line;
                        } else {
                            if (!part.isEmpty()) {
                                line = part + line;
                                part = "";
                                split = line.split(delimiter.toString());
                            }
                        }

                        if (part.isEmpty()) {
                            String partition = split[partitionColumn].replaceAll("\\W", "");

                            if (partition.isEmpty()) {
                                partition = "UNDEFINED";
                            }

                            if (!partitions.containsKey(partition)) {
                                String partitionPath;
                                BufferedWriter bufferedWriter;

                                if (readable) {
                                    partitionPath = csvFile.getParent();
                                    bufferedWriter = new BufferedWriter(new FileWriter(partitionPath + "/" + partition + ".csv"));

                                    if (header) {
                                        bufferedWriter.append(fileHeader + "\r\n");
                                    }
                                } else {
                                    partitionPath = csvFile.getParent() + "/" + partition;
                                    Files.createDirectories(Paths.get(partitionPath));
                                    bufferedWriter = new BufferedWriter(new FileWriter(partitionPath + "/" + UUID.randomUUID() + ".csv"));
                                }

                                partitions.put(partition, bufferedWriter);
                            }

                            partitions.get(partition).append(line + "\r\n");
                        }
                    }
                } else {
                    fileHeader = line;
                }

                lineNumber++;
            }
        }

        //Flush and close the output stream.
        partitions.forEach((k, v) -> {
            try {
                v.flush();
                v.close();
            } catch (IOException ex) {
                Logger.getLogger(this.getClass()).error("Error [" + ex + "] closing writer");
                System.exit(1);
            }
        });
    }

    /**
     * Secure split mode for untrusted but slower files.
     *
     * @throws IOException
     */
    private void secureSplit() throws IOException {
        int lineNumber = 0;
        String[] fileHeader = null;
        HashMap<String, CsvWriter> partitions = new HashMap<>();

        LOG.info("Splitting CSV in secure mode");

        //Writer. 
        CsvWriterSettings writerSettings = new CsvWriterSettings();
        writerSettings.getFormat().setDelimiter(delimiter);
        writerSettings.getFormat().setQuote(quote);
        writerSettings.getFormat().setQuoteEscape(quoteEscape);
        writerSettings.setNullValue("");
        writerSettings.setMaxCharsPerColumn(-1);

        //Reader. 
        CsvParserSettings readerSettings = new CsvParserSettings();
        readerSettings.getFormat().setDelimiter(delimiter);
        readerSettings.getFormat().setQuote(quote);
        readerSettings.getFormat().setQuoteEscape(quoteEscape);
        readerSettings.setNullValue("");
        readerSettings.setMaxCharsPerColumn(-1);
        readerSettings.setInputBufferSize(5 * (1024 * 1024));

        CsvParser csvParser = new CsvParser(readerSettings);
        csvParser.beginParsing(csvFile);

        String[] record;

        while ((record = csvParser.parseNext()) != null) {
            if (!(lineNumber == 0 && header)) {
                String partition = record[partitionColumn].replaceAll("\\W", "");

                if (partition.isEmpty()) {
                    partition = "UNDEFINED";
                }

                if (!partitions.containsKey(partition)) {
                    String partitionPath;

                    if (readable) {
                        partitionPath = csvFile.getParent();
                        partitions.put(partition, new CsvWriter(new FileWriter(partitionPath + "/" + partition + ".csv"), writerSettings));

                        if (header) {
                            partitions.get(partition).writeRow(fileHeader);
                        }
                    } else {
                        partitionPath = csvFile.getParent() + "/" + partition;
                        Files.createDirectories(Paths.get(partitionPath));
                        partitions.put(partition, new CsvWriter(new FileWriter(partitionPath + "/" + UUID.randomUUID() + ".csv"), writerSettings));
                    }
                }

                partitions.get(partition).writeRow(record);
            } else {
                fileHeader = record;
            }

            lineNumber++;
        }

        //Stop the parser.
        csvParser.stopParsing();

        //Flush and close the output stream.
        partitions.forEach((k, v) -> {
            v.flush();
            v.close();
        });
    }
}
