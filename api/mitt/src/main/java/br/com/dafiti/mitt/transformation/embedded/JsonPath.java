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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Fernando Saga
 * @author Valdiney V GOMES
 */
public class JsonPath implements Transformable {

    private final String field;
    private final String path;
    private final boolean displayException;

    public JsonPath(String field, String path) {
        this.field = field;
        this.path = path;
        this.displayException = true;
    }

    public JsonPath(String field, String path, String displayException) {
        this.field = field;
        this.path = path;
        this.displayException = Boolean.valueOf(displayException);
    }

    @Override
    public void init() {
    }

    @Override
    public String getValue(
            Parser parser,
            List<Object> record) {

        String json = null;
        String value = "";

        try {
            //Evaluates the field to retrieve a json. 
            json = String.valueOf(parser.evaluate(record, field));

            if (json != null) {
                //Extracts the desired value from json.
                value = com.jayway.jsonpath.JsonPath
                        .parse(json)
                        .read(this.path).toString();
            }
        } catch (Exception ex) {
            if (this.displayException) {
                Logger.getLogger(JsonPath.class.getName()).log(Level.SEVERE, "Error parsing json {0} with path {1}", new Object[]{json, this.path});
            }
        }

        return value;
    }
}
