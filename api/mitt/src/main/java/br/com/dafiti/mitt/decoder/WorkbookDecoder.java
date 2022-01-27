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
package br.com.dafiti.mitt.decoder;

import br.com.dafiti.mitt.settings.WriterSettings;
import com.univocity.parsers.csv.CsvWriter;
import com.univocity.parsers.csv.CsvWriterSettings;
import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

/**
 *
 * @author Valdiney V GOMES
 */
public class WorkbookDecoder implements Decoder {

    /**
     * Decode XLS and XLSX files.
     *
     * @param file Workbook file.
     * @param properties Reader properties.
     * @return CSV File.
     */
    @Override
    public File decode(File file, Properties properties) {
        File decoded = new File(file.getParent() + File.separator + FilenameUtils.removeExtension(file.getName()) + ".csv");

        try {
            String sheetName = properties.getProperty("sheet");
            String dateFormat = properties.getProperty("dateFormat", "yyyy-MM-dd");
            int skip = NumberUtils.toInt(properties.getProperty("skip", "0"));
            int scale = NumberUtils.toInt(properties.getProperty("scale", "4"));

            //Defines the output file write settings.
            WriterSettings writerSettings = new WriterSettings();

            CsvWriterSettings setting = new CsvWriterSettings();
            setting.getFormat().setDelimiter(writerSettings.getDelimiter());
            setting.getFormat().setQuote(writerSettings.getQuote());
            setting.getFormat().setQuoteEscape(writerSettings.getQuoteEscape());
            setting.setNullValue("");
            setting.setMaxCharsPerColumn(-1);
            setting.setHeaderWritingEnabled(true);

            //Defines the writer.
            CsvWriter csvWriter = new CsvWriter(decoded, setting);

            //Reads the workbook. 
            Workbook workbook = WorkbookFactory.create(file);

            //Identifies if should read all sheets or only a specific one. 
            if (sheetName == null) {
                for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                    this.writer(
                            workbook.getSheetAt(i),
                            csvWriter,
                            dateFormat,
                            (i == 0 ? skip : skip + 1),
                            scale);
                }
            } else {
                this.writer(
                        workbook.getSheetAt(
                                workbook.getSheetIndex(sheetName) == -1
                                ? workbook.getActiveSheetIndex()
                                : workbook.getSheetIndex(sheetName)),
                        csvWriter,
                        dateFormat,
                        skip,
                        scale);
            }

            //Flush and close writer.
            csvWriter.flush();
            csvWriter.close();

            //Removes original file.
            Files.delete(file.toPath());
        } catch (Exception ex) {
            Logger.getLogger(WorkbookDecoder.class.getName()).log(Level.SEVERE, "Fail decoding XLS/XLSX file " + file.getName(), ex);
        }

        return decoded;
    }

    /**
     * Write XLS and XLSX data to a CSV file.
     *
     * @param sheet Sheet
     * @param csvWriter CSV Writer
     * @param dateFormat Date format default
     * @param skip Rows to skip default
     * @param scale Number scale default
     */
    private void writer(
            Sheet sheet,
            CsvWriter csvWriter,
            String dateFormat,
            int skip,
            int scale) {

        Iterator<Row> rowIterator = sheet.iterator();
        FormulaEvaluator evaluator = sheet.getWorkbook().getCreationHelper().createFormulaEvaluator();

        while (rowIterator.hasNext()) {
            List<Object> record = new ArrayList<>();
            Row row = rowIterator.next();

            if (row.getRowNum() > (skip - 1)) {
                for (int column = 0; column < row.getLastCellNum(); column++) {
                    //Get a workbook cell. 
                    Cell cell = row.getCell(column, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);

                    //If the cell has a formula (Cell Type#FORMULA), it will be evaluated and the cell content replaced by the formula result.
                    switch (evaluator.evaluateInCell(cell).getCellType()) {
                        case BOOLEAN:
                            record.add(cell.getBooleanCellValue());
                            break;
                        case NUMERIC:
                            //Identifies if cell is a date.
                            if (DateUtil.isCellDateFormatted(cell)) {
                                DateFormat df = new SimpleDateFormat(dateFormat);
                                Date date = cell.getDateCellValue();
                                record.add(df.format(date));

                            } else {
                                BigDecimal value = new BigDecimal(cell.getNumericCellValue());

                                //Identifies if number has decimal scale.
                                if (value.scale() > 0) {
                                    record.add(value.setScale(scale, RoundingMode.HALF_EVEN).toPlainString());
                                } else {
                                    record.add(value.toPlainString());
                                }
                            }

                            break;
                        case STRING:
                            record.add(cell.getStringCellValue());
                            break;
                        default:
                            record.add("");
                            break;
                    }
                }

                csvWriter.writeRow(record);
            }
        }
    }
}
