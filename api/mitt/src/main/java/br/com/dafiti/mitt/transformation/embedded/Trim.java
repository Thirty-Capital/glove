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
package br.com.dafiti.mitt.transformation.embedded;

import br.com.dafiti.mitt.transformation.Parser;
import br.com.dafiti.mitt.transformation.Transformable;
import java.util.List;

/**
 * Trims a string: all, right or left.
 *
 * @author Helio Leal
 */
public class Trim implements Transformable {

    private final String field;
    private final String type;

    public Trim(String field) {
        this.field = field;
        this.type = "";
    }

    public Trim(String field, String type) {
        this.field = field;
        this.type = type;
    }

    @Override
    public void init() {
    }

    @Override
    public String getValue(
            Parser parser,
            List<Object> record) {
        String value;

        switch (this.type) {
            case "RTRIM":
                value = ((String) parser.evaluate(record, field)).replaceAll("\\s+$", "");
                break;
            case "LTRIM":
                value = ((String) parser.evaluate(record, field)).replaceAll("^\\s+", "");
                break;
            default:
                value = ((String) parser.evaluate(record, field)).trim();
                break;
        }

        return value;
    }
}