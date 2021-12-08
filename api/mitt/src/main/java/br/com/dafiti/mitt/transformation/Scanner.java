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
 * NON INFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * 
 */
package br.com.dafiti.mitt.transformation;

import br.com.dafiti.mitt.model.Field;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

/**
 *
 * @author Valdiney V GOMES
 */
public class Scanner {

    private static Scanner scanner;

    private final Map<String, Transformable> instances = new ConcurrentHashMap<>();
    private final Map<String, Class<? extends Transformable>> clazzes = new ConcurrentHashMap<>();

    private static final String LIST_OPEN = "[[";
    private static final String LIST_CLOSE = "]]";
    private static final String FREEZE_OPEN = "**";
    private static final String FREEZE_CLOSE = "**";

    /**
     *
     */
    private Scanner() {
        ClassPathScanningCandidateComponentProvider provider = new ClassPathScanningCandidateComponentProvider(false);
        provider.addIncludeFilter(new AssignableTypeFilter(Transformable.class));
        Set<BeanDefinition> transformations = provider.findCandidateComponents("br/com/dafiti");

        transformations.forEach(transformation -> {
            try {
                Class<?> clazz = Class.forName(transformation.getBeanClassName());
                this.clazzes.put(clazz.getSimpleName().toLowerCase(), (Class<? extends Transformable>) clazz);
            } catch (ClassNotFoundException ex) {
                Logger.getLogger(Scanner.class.getName()).log(Level.SEVERE, "Fail instantiating class " + transformation.getBeanClassName(), ex);
            }
        });
    }

    /**
     *
     * @return
     */
    public static Scanner getInstance() {
        if (scanner == null) {
            synchronized (Scanner.class) {
                if (scanner == null) {
                    scanner = new Scanner();
                }

            }
        }

        return scanner;
    }

    /**
     *
     * @param content
     * @return
     */
    public Field scan(String content) {
        return this.scan(content, true);
    }

    /**
     *
     * @param content
     * @param original
     * @return
     */
    public Field scan(String content, boolean original) {
        String fieldName;
        Field field;
        String alias = "";
        Transformable instance = null;

        //Identifies if should parse content.
        if (content.contains("::")) {
            List<String> parameters = new ArrayList<>();
            List<String> transformationClassParameterListItem = new ArrayList<>();

            //Sets an ID for each transformation. 
            String id = DigestUtils.md5Hex(content).toUpperCase();

            //Identifies if a field name was provided.
            fieldName = StringUtils.substringBefore(content, "::");

            //Defines the field name as anonymous plus transformation ID if it was not provided.
            fieldName = fieldName.isEmpty() ? ("anonymous_" + id) : fieldName;

            //Identifies if it has a transformation instance in cache.
            if (instances.containsKey(id)) {
                instance = instances.get(id);
            } else {
                //Identifies each part of the function. 
                String transformation = StringUtils.substringAfter(content, "::");
                String transformationClass = StringUtils.substringBefore(transformation, "(").toLowerCase();
                String transformationClassParameter = StringUtils.substringBeforeLast(StringUtils.substringAfter(transformation, "("), ")");
                String transformationClassParameterList = StringUtils.substringBefore(StringUtils.substringAfter(transformationClassParameter, LIST_OPEN), LIST_CLOSE);

                //Supports parameter parser. 
                String token = "";
                boolean partial = false;

                //Identifies each item in a parameter list.  
                for (String string : StringUtils.split(transformationClassParameterList, ',')) {
                    if (string.contains("(") && string.contains(")")) {
                        transformationClassParameterListItem.add(string);
                    } else if (string.contains("(")) {
                        partial = true;
                        token = string + ",";
                    } else if (partial && !(string.contains("(") || string.contains(")"))) {
                        token += string + ",";
                    } else if (string.contains(")") && partial) {
                        partial = false;
                        transformationClassParameterListItem.add(token + string);
                        token = "";
                    } else {
                        transformationClassParameterListItem.add(string);
                    }
                }

                //Identifies if any parameter should not be parsed.
                if (transformationClassParameter.startsWith(FREEZE_OPEN)
                        && transformationClassParameter.endsWith(FREEZE_CLOSE)) {
                    //**...** identifies frozen parameter.
                    parameters.add(
                            transformationClassParameter
                                    .replace(FREEZE_OPEN, "")
                                    .replace(FREEZE_CLOSE, ""));
                } else {
                    //Otherwise, it encodes parameters using encodeBase64String to avoid break the parser.
                    String[] encoded = StringUtils.split(
                            transformationClassParameter = transformationClassParameter.replace(
                                    transformationClassParameterList,
                                    Base64.encodeBase64String(
                                            String.join("+", transformationClassParameterListItem).getBytes()
                                    )
                            ), ',');

                    //After encode, identifies if frozen parameters are intact.
                    token = "";
                    partial = false;

                    for (String value : encoded) {
                        if (value.startsWith(FREEZE_OPEN)
                                && value.endsWith(FREEZE_CLOSE)) {
                            parameters.add(value.replace(FREEZE_OPEN, "").replace(FREEZE_CLOSE, ""));
                        } else if (value.startsWith(FREEZE_OPEN)) {
                            partial = true;
                            token = value + ",";
                        } else if (value.endsWith(FREEZE_CLOSE)) {
                            partial = false;
                            parameters.add((token + value).replace(FREEZE_OPEN, "").replace(FREEZE_CLOSE, ""));
                        } else if (partial) {
                            token = token + value + ",";
                        } else {
                            parameters.add(value.replace(FREEZE_OPEN, "").replace(FREEZE_CLOSE, ""));
                        }
                    }
                }

                //Identifies which transformation should run.
                if (this.clazzes.containsKey(transformationClass)) {
                    try {
                        //Instantiate transformation class by it name. 
                        Class<? extends Transformable> clazz = this.clazzes.get(transformationClass);
                        Constructor[] constructors = clazz.getDeclaredConstructors();

                        //Identifies if the transformation has parameter. 
                        if (constructors.length == 0) {
                            instance = clazz.getDeclaredConstructor().newInstance();
                        } else {
                            //Iterates each transformation constructors. 
                            for (Constructor constructor : constructors) {
                                int transformationParameterNumber = parameters.size();

                                //Identifies parameter number on each constructor. 
                                if (transformationParameterNumber == 0) {
                                    instance = clazz.getDeclaredConstructor().newInstance();
                                    break;
                                } else {
                                    if (constructor.getParameterCount() == transformationParameterNumber) {
                                        List<Object> constructorParameters = new ArrayList<>();

                                        //Iterates each constructor parameter. 
                                        for (int i = 0; i < transformationParameterNumber; i++) {
                                            //Identifies each parameter type. 
                                            switch (constructor.getParameterTypes()[i].getSimpleName()) {
                                                case "List":
                                                    List<Field> fieldList = new ArrayList<>();

                                                    for (String parameter : parameters) {
                                                        //If the parameter is a list, it should be decoded using decodeBase64. 
                                                        if (parameter.startsWith(LIST_OPEN)
                                                                && parameter.endsWith(LIST_CLOSE)) {
                                                            String[] fields = new String(
                                                                    Base64.decodeBase64(
                                                                            parameter.replace(LIST_OPEN, "").replace(LIST_CLOSE, ""))
                                                            ).split("\\+");

                                                            for (String parameterField : fields) {
                                                                fieldList.add(this.scan(parameterField.trim()));
                                                            }
                                                        }
                                                    }

                                                    constructorParameters.add(fieldList);
                                                    break;
                                                case "Field":
                                                    constructorParameters.add(this.scan(parameters.get(i).trim()));
                                                case "String":
                                                    constructorParameters.add(parameters.get(i).trim());
                                                    break;
                                                default:
                                                    if (constructor.getParameterTypes()[i].isPrimitive()) {
                                                        Method method = constructor.getParameterTypes()[i].getDeclaredMethod("valueOf", String.class);
                                                        constructorParameters.add(method.invoke(parameters.get(i).trim()));
                                                    }

                                                    break;
                                            }
                                        }

                                        instance = (Transformable) constructor.newInstance(constructorParameters.toArray());
                                    }
                                }
                            }
                        }

                        //Puts instance of each transformation in cache.  
                        if (instance != null) {
                            instances.put(id, instance);
                        }
                    } catch (InstantiationException
                            | IllegalAccessException
                            | IllegalArgumentException
                            | InvocationTargetException
                            | NoSuchMethodException
                            | SecurityException ex) {

                        Logger.getLogger(Scanner.class.getName()).log(Level.SEVERE, "Fail evaluating transformation " + transformationClass + " with parameter  " + transformationClassParameter, ex);
                    }
                }
            }
        } else if (content.contains(">>")) {
            //Identifies if it should rename a field.
            fieldName = StringUtils.substringBefore(content, ">>");
            alias = StringUtils.substringAfter(content, ">>");
        } else {
            fieldName = content;
        }

        if (instance == null) {
            field = new Field(fieldName, alias);
            field.setExpression(content);
        } else {
            field = new Field(fieldName, instance, original);
            field.setExpression(content);
        }

        return field;
    }
}
