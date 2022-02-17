/*
 * Copyright (c) 2022 Dafiti Group
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
package br.com.dafiti.metadata.schema;

import br.com.dafiti.metadata.model.Field;

/**
 *
 * @author Helio Leal
 */
public class Bigquery implements Metadata {

    @Override
    public void generateNull(Field field, String name) {
        field.getMetadata().add("{\"field\":\"" + name + "\",\"type\":\"string\",\"length\":255}");
        field.getSchema().add("{\"name\":\"" + name + "\",\"type\":\"STRING\"}");
    }

    @Override
    public void generateString(Field field, String name, int length) {
        field.getMetadata().add("{\"field\":\"" + name + "\",\"type\":\"string\",\"length\":" + length + "}");
        field.getSchema().add("{\"name\":\"" + name + "\",\"type\":\"STRING\"}");
    }

    @Override
    public void generateInteger(Field field, String name) {
        field.getMetadata().add("{\"field\":\"" + name + "\",\"type\":\"integer\"}");
        field.getSchema().add("{\"name\":\"" + name + "\",\"type\":\"INTEGER\"}");
    }

    @Override
    public void generateNumber(Field field, String name) {
        field.getMetadata().add("{\"field\":\"" + name + "\",\"type\":\"number\"}");
        field.getSchema().add("{\"name\":\"" + name + "\",\"type\":\"FLOAT\"}");
    }

    @Override
    public void generateBigNumber(Field field, String name) {
        this.generateNumber(field, name);
    }

    @Override
    public void generateTimestamp(Field field, String name) {
        field.getMetadata().add("{\"field\":\"" + name + "\",\"type\":\"string\",\"length\":19}");
        field.getSchema().add("{\"name\":\"" + name + "\",\"type\":\"TIMESTAMP\"}");
    }

    @Override
    public void generateDate(Field field, String name) {
        field.getMetadata().add("{\"field\":\"" + name + "\",\"type\":\"string\",\"length\":19}");
        field.getSchema().add("{\"name\":\"" + name + "\",\"type\":\"DATE\"}");
    }

    @Override
    public void generateBoolean(Field field, String name) {
        field.getMetadata().add("{\"field\":\"" + name + "\",\"type\":\"boolean\"}");
        field.getSchema().add("{\"name\":\"" + name + "\",\"type\":\"BOOLEAN\"}");
    }
}