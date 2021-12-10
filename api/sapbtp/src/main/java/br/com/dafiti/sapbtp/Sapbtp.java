/*
 * Copyright (c) 2021 Dafiti Group
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
package br.com.dafiti.sapbtp;

import br.com.dafiti.mitt.Mitt;
import br.com.dafiti.mitt.cli.CommandLineInterface;
import br.com.dafiti.mitt.model.Configuration;
import br.com.dafiti.mitt.transformation.embedded.Concat;
import br.com.dafiti.mitt.transformation.embedded.Now;
import java.io.FileReader;
import java.util.Base64;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

/**
 *
 * @author Helio Leal
 */
public class Sapbtp {

    private static final Logger LOG = Logger.getLogger(Sapbtp.class.getName());
    private static final String SAPBTP_TOKEN_URL = "https://hdbprd.authentication.us10.hana.ondemand.com/oauth/token";
    private static final String SAPBTP_ENDPOINT = "https://gfg-monitor-prd.cfapps.us10.hana.ondemand.com/";

    /**
     *
     * @param args cli parameteres provided by command line.
     */
    public static void main(String[] args) {
        LOG.info("GLOVE - SAPBTP API extractor started");

        //Define the mitt.
        Mitt mitt = new Mitt();

        try {
            //Defines parameters.
            mitt.getConfiguration()
                    .addParameter("c", "credentials", "Credentials file", "", true, false)
                    .addParameter("o", "output", "Output file", "", true, false)
                    .addParameter("f", "field", "Fields to be retrieved from an endpoint", "", true, false)
                    //.addParameter("e", "endpoint", "Endpoint name", "", true, false)
                    //.addParameter("p", "parameters", "(Optional) Endpoint parameters", "", true, true)
                    //.addParameter("b", "object", "(Optional) Json object", "", true, true)
                    //.addParameter("g", "paginate", "(Optional) Identifies if the endpoint has pagination", false)
                    .addParameter("a", "partition", "(Optional)  Partition, divided by + if has more than one field", "")
                    .addParameter("k", "key", "(Optional) Unique key, divided by + if has more than one field", "");

            //Reads the command line interface. 
            CommandLineInterface cli = mitt.getCommandLineInterface(args);

            //Defines output file.
            mitt.setOutputFile(cli.getParameter("output"));

            //Defines fields.
            Configuration configuration = mitt.getConfiguration();

            if (cli.hasParameter("partition")) {
                configuration
                        .addCustomField("partition_field", new Concat((List) cli.getParameterAsList("partition", "\\+")));
            }

            if (cli.hasParameter("key")) {
                configuration
                        .addCustomField("custom_primary_key", new Concat((List) cli.getParameterAsList("key", "\\+")));
            }

            configuration
                    .addCustomField("etl_load_date", new Now())
                    .addField(cli.getParameterAsList("field", "\\+"));

            //Reads the credentials file. 
            JSONParser parser = new JSONParser();
            JSONObject credentials = (JSONObject) parser.parse(new FileReader(cli.getParameter("credentials")));

            //Retrieves API credentials. 
            String username = credentials.get("username").toString();
            String password = credentials.get("password").toString();

            //Connect to the API. 
            try (CloseableHttpClient client = HttpClients.createDefault()) {
                HttpGet httpGet = new HttpGet(SAPBTP_TOKEN_URL);

                //Sets default URI parameters. 
                URIBuilder uriBuilder = new URIBuilder(httpGet.getURI())
                        .addParameter("grant_type", "client_credentials")
                        .addParameter("response_type", "token");

                //Sets URI parameters. 
                httpGet.setURI(uriBuilder.build());
                
                String encoding = Base64.getEncoder().encodeToString((username + ":" + password).getBytes("UTF-8"));
                
                //Sets Headers
                httpGet.addHeader("Authorization", "Basic " + encoding);

                //Executes a request. 
                CloseableHttpResponse response = client.execute(httpGet);

                //Gets a reponse entity. 
                String entity = EntityUtils.toString(response.getEntity(), "UTF-8");

                if (!entity.isEmpty()) {
                    
                    System.out.println("entity: " + entity);

                } else {
                    throw new Exception("Empty response entity for request " + httpGet.getURI());
                }

            }

            /*   HttpResponse<String> response = Unirest.get("https://hdbprd.authentication.us10.hana.ondemand.com/oauth/token?grant_type=client_credentials&response_type=token")
                    .header("Authorization", "Basic c2ItR0ZHX1hTVUFBX0NMT1VEIXQxMTk3NzowaE5HUWEyZXFOTHlVbVdBREd2UUpRdUlNUUk9")
                    .header("cache-control", "no-cache")
                    .header("Postman-Token", "7986206d-a7fd-4ba0-a5fb-f1a62e917fe6")
                    .asString();

            if (!token.getBody().isEmpty()) {

            }*/
        } catch (Exception ex) {
            LOG.log(Level.SEVERE, "GLOVE - SAPBTP API extractor fail: ", ex);
            System.exit(1);
        } finally {
            mitt.close();
        }

        LOG.info("GLOVE - SAPBTP API extractor finalized");

    }

}
