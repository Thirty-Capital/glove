/*
 * Copyright (c) 2019 Dafiti Group
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
package br.com.dafiti.mitt.transformation.embedded;

import br.com.dafiti.mitt.transformation.Parser;
import br.com.dafiti.mitt.transformation.Transformable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Valdiney V GOMES
 */
public class DateFormat implements Transformable {

    private final Object field;
    private final String inputFormat;
    private final String outputFormat;
    private final Locale locale;

    public DateFormat(String field, String outputFormat) {
        this.field = field;
        this.outputFormat = outputFormat;
        this.inputFormat = "yyyy-MM-dd HH:mm:ss";
        this.locale = Locale.ENGLISH;
    }

    public DateFormat(String field, String inputFormat, String outputFormat) {
        this.field = field;
        this.inputFormat = inputFormat;
        this.outputFormat = outputFormat;
        this.locale = Locale.ENGLISH;
    }

    public DateFormat(String field, String inputFormat, String outputFormat, String language, String country) {
        this.field = field;
        this.inputFormat = inputFormat;
        this.outputFormat = outputFormat;
        this.locale = new Locale(language, country);
    }

    @Override
    public void init() {
    }

    @Override
    public String getValue(
            Parser parser,
            List<Object> record) {

        String value = new String();
        Object date = parser.evaluate(record, field);

        if (date != null) {
            if (date instanceof Date) {
                value = new SimpleDateFormat(outputFormat, locale).format(date);
            } else if (!((String) date).isEmpty()) {
                try {
                    if (inputFormat.equalsIgnoreCase("UNIXTIMEMILLIS")) {
                        value = new SimpleDateFormat(outputFormat, locale)
                                .format(new Date(Long.valueOf((String) date)));
                    } else if (inputFormat.equalsIgnoreCase("UNIXTIME")) {
                        value = new SimpleDateFormat(outputFormat, locale)
                                .format(new Date(Long.valueOf((String) date) * 1000L));
                    } else {
                        value = new SimpleDateFormat(outputFormat, locale)
                                .format(
                                        new SimpleDateFormat(inputFormat, locale)
                                                .parse((String) date));
                    }
                } catch (ParseException ex) {
                    Logger.getLogger(DateFormat.class.getName()).log(Level.SEVERE, "Error converting data " + date, ex);
                }
            }
        }

        return value;
    }
}
