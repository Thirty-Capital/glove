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
package br.com.dafiti.facebook;

import br.com.dafiti.mitt.Mitt;
import br.com.dafiti.mitt.exception.DuplicateEntityException;
import br.com.dafiti.mitt.transformation.embedded.Concat;
import br.com.dafiti.mitt.transformation.embedded.Now;
import com.facebook.ads.sdk.APIContext;
import com.facebook.ads.sdk.APIException;
import com.facebook.ads.sdk.APINodeList;
import com.facebook.ads.sdk.AdAccount;
import com.facebook.ads.sdk.AdsInsights;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Valdiney V GOMES
 */
public class AdsInsight {

    private final APIContext apiContext;
    private final String output;
    private final List<String> adAccount;
    private final String startDate;
    private final String endDate;
    private final List key;
    private final List partition;
    private final List<String> fields;
    private final List<String> breakdowns;
    private final List<String> attributes;
    private final String filtering;

    private static final Logger LOG = Logger.getLogger(AdsInsight.class.getName());

    public AdsInsight(
            APIContext apiContext,
            String output,
            List<String> adAccount,
            String startDate,
            String endDate,
            List key,
            List partition,
            List fields,
            List breakdowns,
            List attibutes,
            String filtering) {

        this.apiContext = apiContext;
        this.adAccount = adAccount;
        this.output = output;
        this.startDate = startDate;
        this.endDate = endDate;
        this.key = key;
        this.partition = partition;
        this.fields = fields;
        this.breakdowns = breakdowns;
        this.attributes = attibutes;
        this.filtering = filtering;
    }

    /**
     *
     */
    void extract() throws DuplicateEntityException, APIException {
        //Defines a MITT instance. 
        Mitt mitt = new Mitt();
        mitt.setOutputFile(this.output);

        //Defines fields.
        mitt.getConfiguration()
                .addCustomField("partition_field", new Concat(this.partition))
                .addCustomField("custom_primary_key", new Concat(this.key))
                .addCustomField("etl_load_date", new Now());

        //Defines the default report attributes
        if (this.attributes.isEmpty()) {
            this.attributes.add("account_id");
            this.attributes.add("campaign_id");
            this.attributes.add("adset_id");
            this.attributes.add("ad_id");
            this.attributes.add("campaign_name");
        }

        //Defines the output fields.
        mitt.getConfiguration().addField(this.attributes);

        if (!this.breakdowns.isEmpty()) {
            mitt.getConfiguration().addField(this.breakdowns);
        }

        //Identifies if fields parameter was filled.
        if (!this.fields.isEmpty()) {
            for (String field : this.fields) {
                mitt.getConfiguration().addCustomField(field);
            }
        }

        //Identifies original fields.
        List<String> originalFields = mitt.getConfiguration().getOriginalFieldName();

        //Iterates for each account.
        this.adAccount.forEach(account -> {
            try {
                LOG.log(Level.INFO, "Retrieving adInsights from account {0}", account.trim());

                AdAccount adAccount = new AdAccount(account.trim(), this.apiContext);
                AdAccount.APIRequestGetInsights adInsightsRequest = adAccount.getInsights();

                //Defines some filters.
                adInsightsRequest.setLevel(AdsInsights.EnumLevel.VALUE_AD);
                adInsightsRequest.setTimeIncrement("1");
                adInsightsRequest.setTimeRange("{\"since\":\"" + this.startDate + "\",\"until\":\"" + this.endDate + "\"}");
                adInsightsRequest.setActionAttributionWindows(
                        Arrays.asList(
                                AdsInsights.EnumActionAttributionWindows.VALUE_DEFAULT
                        )
                );

                //Define the filters.
                if (this.filtering != null) {
                    LOG.log(Level.INFO, "Filter: {0}", this.filtering);
                    adInsightsRequest.setParam("filtering", this.filtering);
                }

                //Identifies if report has breakdowns.
                if (!this.breakdowns.isEmpty()) {
                    adInsightsRequest.setBreakdowns(String.join(",", this.breakdowns));
                }

                //Define report attributes to be requested.
                this.attributes.forEach((attribute) -> {
                    if (!this.breakdowns.contains(attribute)) {
                        adInsightsRequest.requestField(attribute);
                    }
                });

                //Request campaign fields.
                APINodeList<AdsInsights> adsInsights = adInsightsRequest.execute();

                //Enables auto pagination.
                adsInsights = adsInsights.withAutoPaginationIterator(true);

                for (AdsInsights adsInsight : adsInsights) {
                    List record = new ArrayList();

                    originalFields.forEach((field) -> {
                        JsonObject jsonObject = adsInsight.getRawResponseAsJsonObject();

                        //Identifies if the field exists. 
                        if (jsonObject.has(field)) {
                            JsonElement jsonElement = jsonObject.get(field);

                            //Identifies if the fiels is a primitive.
                            if (jsonElement.isJsonPrimitive()) {
                                record.add(jsonElement.getAsString());
                            } else {
                                record.add(jsonElement);
                            }
                        } else {
                            record.add(null);
                        }
                    });

                    mitt.write(record);
                }
            } catch (APIException ex) {
                LOG.log(Level.SEVERE, "Fail retrieving AdsInsights from account {0}. Exception: {1}", new Object[]{account.trim(), ex});
                System.exit(1);
            }
        });

        mitt.close();
    }
}
