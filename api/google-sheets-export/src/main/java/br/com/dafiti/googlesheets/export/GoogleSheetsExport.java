/*
 * Copyright (c) 2020 Dafiti Group
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
package br.com.dafiti.googlesheets.export;

import br.com.dafiti.googlesheets.export.api.GoogleSheetsApi;
import br.com.dafiti.googlesheets.export.model.SheetDetails;
import br.com.dafiti.mitt.Mitt;
import br.com.dafiti.mitt.cli.CommandLineInterface;
import br.com.dafiti.mitt.exception.DuplicateEntityException;
import com.univocity.parsers.common.routine.InputDimension;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import com.univocity.parsers.csv.CsvRoutines;
import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Helio Leal
 */
public class GoogleSheetsExport {

    public static final int CELLS_LIMIT = 5000000;
    public static final int POOL = 10000;

    /**
     * @param args the command line arguments
     *
     * @throws br.com.dafiti.mitt.exception.DuplicateEntityException
     * @throws java.security.GeneralSecurityException
     * @throws java.io.IOException
     * @throws java.lang.InterruptedException
     */
    public static void main(String[] args) throws
            DuplicateEntityException,
            GeneralSecurityException,
            IOException,
            InterruptedException {

        System.out.println("Google sheets export started.");

        //Define the mitt.
        Mitt mitt = new Mitt();

        //Define parameters. 
        mitt.getConfiguration()
                .addParameter("c", "credentials", "Credentials file", "", true, false)
                .addParameter("s", "spreadsheet", "Id of the spreadsheet", "", true, false)
                .addParameter("i", "input", "Input file path and file name", "", true, false)
                .addParameter("t", "sheet", "Define sheet name", "", true, false)
                .addParameter("sl", "sleep", "(Optional) Sleep time in seconds at one request and another; 0 is default", "0")
                .addParameter("m", "method", "(Optional) Update method, 0 for full, 1 append; 0 is default", "0")
                .addParameter("d", "debug", "(Optional) Identify if it is debug mode; 0 is default", "0")
                .addParameter("de", "delimiter", "(Optional) Delimiter, default is semicolon(;) ", ";")
                .addParameter("q", "quote", "(Optional) Quote, default is double quotes(\") ", "\"")
                .addParameter("qe", "quote_escape", "(Optional) Quote Escaoe, default is double quotes(\") ", "\"")
                .addParameter("t", "timeout", "(Optional) Read and connect timeout; 6 is default", "6")
                .addParameter("io", "input_option", "(Optional) Determines how input data should be interpreted; USER_ENTERED is default", "USER_ENTERED");

        //Read the command line interface. 
        CommandLineInterface cli = mitt.getCommandLineInterface(args);

        //Identifies if spreadsheet id is not empty.
        if (!cli.getParameter("spreadsheet").isEmpty()) {

            //Define spreadsheet API manager.
            GoogleSheetsApi api
                    = new GoogleSheetsApi(cli.getParameter("sheet"), cli.getParameterAsInteger("debug") == 1, cli.getParameter("input_option"))
                            .authenticate(cli.getParameter("credentials"), cli.getParameter("spreadsheet"), cli.getParameterAsInteger("timeout"));

            //Define the input file.
            File file = new File(cli.getParameter("input"));

            //Get CSV informations.
            InputDimension inputDimension = new CsvRoutines(getSettings(true, cli)).getInputDimension(file);

            //Calculate the number of cells that will be written.
            long cells = (inputDimension.columnCount() * inputDimension.rowCount());

            System.out.println("[" + file.getAbsolutePath() + "] rows: " + inputDimension.rowCount() + ", cols: " + inputDimension.columnCount() + " (" + cells + " cells will be written)");

            //Get number of filled cells on spreadsheet.
            long cellsCount = api.getCellsCount();

            //Number of cells that spreadsheet will have.
            cells = cells + cellsCount;

            System.out.println("[" + api.getSpreadsheetTitle() + "] cells: " + cellsCount + ", after update: " + cells + " (Limit is " + CELLS_LIMIT + ")");

            //Identify if cells count is over the suppported on Google Sheets.
            if (cells <= CELLS_LIMIT) {

                //Get Details of a sheet.
                SheetDetails sheet = api.getSheetDetails();

                //Identify if sheet was found.
                if (sheet.getId() >= 0) {
                    System.out.println("Input option: [" + cli.getParameter("input_option") + "]");

                    //Identify if should remove unused columns.
                    if (sheet.getColumnCount() > inputDimension.columnCount()) {
                        api.removeColumns(sheet, inputDimension.columnCount());
                    }

                    //Identify if should add new columns.
                    if (sheet.getColumnCount() < inputDimension.columnCount()) {
                        api.addColumns(sheet, inputDimension.columnCount());
                    }

                    //Range init.
                    int startIndex = 1;
                    int pool = (inputDimension.rowCount() < POOL) ? (int) inputDimension.rowCount() : POOL;

                    //Define a csv parser.
                    CsvParser csvParser;

                    //Identify update method.                    
                    switch (cli.getParameterAsInteger("method")) {
                        // Append Method
                        case 1:
                            System.out.println("Update method: [APPEND]");

                            //If at least one cell is filled, append ignore header.
                            csvParser = new CsvParser(getSettings(api.hasValues(sheet, startIndex), cli));
                            break;

                        // Full method
                        default:
                            System.out.println("Update method: [FULL]");

                            //Full mehotd doesn't extract header.
                            csvParser = new CsvParser(getSettings(false, cli));

                            //Clear cells from a sheet.
                            api.cleanCells(sheet);
                            break;
                    }

                    //Init a parser.
                    csvParser.beginParsing(file);

                    //Value being processed.
                    String[] line;

                    //Values that will fill spreadsheet.
                    List<List<Object>> values = new ArrayList();

                    //Count records.
                    int count = 0;

                    //Process each csv record.
                    while ((line = csvParser.parseNext()) != null) {
                        List<Object> record = new ArrayList();

                        for (String column : line) {
                            //Google sheets SDK doesn't accept null values, when null, put empty on the place of it.
                            if (column != null) {
                                record.add(column);
                            } else {
                                record.add("");
                            }
                        }
                        values.add(record);
                        count++;

                        //Identify if should flush data into google spreadsheet.
                        if (count == pool) {
                            api.append(values, startIndex, inputDimension.columnCount(), pool);

                            //Increment ranges.
                            startIndex += values.size();
                            pool += values.size();

                            //Clears values already saved.
                            values.clear();

                            //Identify if has sleep time until next API call.
                            if (cli.getParameterAsInteger("sleep") > 0) {
                                try {
                                    System.out.println("Sleeping " + cli.getParameterAsInteger("sleep") + " seconds until next API call");

                                    Thread.sleep(Long.valueOf(cli.getParameterAsInteger("sleep") * 1000));
                                } catch (InterruptedException ex) {
                                    System.err.println(ex.getMessage());
                                }
                            }
                        }
                    }

                    //Identify if there is some records left.
                    if (values.size() > 0) {
                        api.append(values, startIndex, inputDimension.columnCount(), pool);
                    }

                } else {
                    System.err.println("Sheet " + cli.getParameter("sheet") + " was not found on spreadsheet " + cli.getParameter("spreadsheet"));
                }
            } else {
                System.err.println("Cells count is over the limit of " + CELLS_LIMIT);
            }
        } else {
            System.err.println("Parameter spreadsheet is empty, please set it up.");
        }

        mitt.close();

        System.out.println("Google sheets export finalized.");
    }

    /**
     * Define CSV settings.
     *
     * @param headerExtraction extract header from CSV.
     * @param cli
     * @return
     */
    public static CsvParserSettings getSettings(boolean headerExtraction, CommandLineInterface cli) {
        //Define a settings.
        CsvParserSettings parserSettings = new CsvParserSettings();
        parserSettings.setNullValue("");
        parserSettings.setMaxCharsPerColumn(-1);

        //Define format settings.
        parserSettings.getFormat().setDelimiter(cli.getParameter("delimiter"));
        parserSettings.getFormat().setQuote(cli.getParameter("quote").charAt(0));
        parserSettings.getFormat().setQuoteEscape(cli.getParameter("quote_escape").charAt(0));

        //Define the input buffer.
        parserSettings.setInputBufferSize(2 * (1024 * 1024));
        parserSettings.setHeaderExtractionEnabled(headerExtraction);

        return parserSettings;
    }
}
