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
package br.com.dafiti.s3;

import br.com.dafiti.mitt.Mitt;
import br.com.dafiti.mitt.cli.CommandLineInterface;
import br.com.dafiti.mitt.exception.DuplicateEntityException;
import br.com.dafiti.mitt.transformation.embedded.Concat;
import br.com.dafiti.mitt.transformation.embedded.Now;
import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.s3.transfer.Download;
import com.amazonaws.services.s3.transfer.Transfer.TransferState;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.io.FileUtils;
import org.joda.time.LocalDateTime;
import java.util.UUID;

/**
 * S3 File Extractor
 *
 * @author Helio Leal
 */
public class S3 {

    /**
     * Main class
     *
     * @param args command line parameters
     */
    public static void main(String[] args) {
        Logger.getLogger(S3.class.getName()).info("Glove - S3 Extractor started.");

        //Defines a MITT instance. 
        Mitt mitt = new Mitt();

        try {
            //Defines parameters.
            mitt.getConfiguration()
                    .addParameter("o", "output", "Output file", "", true, false)
                    .addParameter("b", "bucket", "S3 Bucket name", "", true, false)
                    .addParameter("p", "prefix", "Object prefix (folder/subfolder/ or folder/subfolder/key.csv)", "", true, true)
                    .addParameter("f", "field", "Fields to be extracted from the file", "", true, false)
                    .addParameter("pf", "profile", "(Optional) AWS credentials profile name", "default")
                    .addParameter("rg", "region", "(Optional) AWS region", "us-east-1")
                    .addParameter("sd", "start_date", "(Optional) Object modified date since", "")
                    .addParameter("st", "start_time", "(Optional) Object modified time since", "00:00:00")
                    .addParameter("ed", "end_date", "(Optional) Object modified date to", "")
                    .addParameter("et", "end_time", "(Optional) Object modified time to", "23:59:59")
                    .addParameter("fr", "filter", "(Optional) Object key filter", "")
                    .addParameter("r", "retries", "(Optional)(Default is 3) Identifies how many retries will do when limit rate exceeded.", "3")
                    .addParameter("d", "delimiter", "(Optional) File delimiter; ';' as default", ";")
                    .addParameter("p", "partition", "(Optional)  Partition, divided by + if has more than one field")
                    .addParameter("k", "key", "(Optional) Unique key, divided by + if has more than one field", " ")
                    .addParameter("n", "no_header", "(Optional)(Default is true) File has heaer", false)
                    .addParameter("pr", "properties", "(Optional) Reader properties.", "");

            //Defines the command line interface. 
            CommandLineInterface cli = mitt.getCommandLineInterface(args);

            //Defines output file.
            mitt.setOutputFile(cli.getParameter("output"));

            //Defines fields.
            mitt.getConfiguration()
                    .addCustomField("partition_field", new Concat((List) cli.getParameterAsList("partition", "\\+")))
                    .addCustomField("custom_primary_key", new Concat((List) cli.getParameterAsList("key", "\\+")))
                    .addCustomField("etl_load_date", new Now())
                    .addField(cli.getParameterAsList("field", "\\+"));

            //Defines a S3 client
            AmazonS3 amazonS3Client = AmazonS3ClientBuilder
                    .standard()
                    .withCredentials(new ProfileCredentialsProvider(cli.getParameter("profile")))
                    .withRegion(cli.getParameter("region"))
                    .build();

            //Lists objects of a folder.
            ObjectListing listing = amazonS3Client.listObjects(cli.getParameter("bucket"), cli.getParameter("prefix"));

            List<S3ObjectSummary> s3ObjectSummaries = listing.getObjectSummaries();

            while (listing.isTruncated()) {
                listing = amazonS3Client.listNextBatchOfObjects(listing);
                s3ObjectSummaries.addAll(listing.getObjectSummaries());
            }

            //Defines the output path. 
            Path outputPath = Files.createTempDirectory("s3_" + UUID.randomUUID());

            Logger.getLogger(S3.class.getName()).log(Level.INFO, "Downloading files from: {0}/{1}", new Object[]{cli.getParameter("bucket"), cli.getParameter("prefix")});

            for (S3ObjectSummary s3ObjectSummary : s3ObjectSummaries) {
                if (s3ObjectSummary.getSize() > 0) {
                    boolean match = true;
                    boolean interval = true;

                    String objectKey = s3ObjectSummary.getKey();
                    LocalDateTime updatedDate = LocalDateTime.fromDateFields(s3ObjectSummary.getLastModified());

                    if ((cli.getParameter("filter") != null)
                            || (cli.getParameter("start_date") != null
                            && cli.getParameter("end_date") != null)) {

                        //Identifies if should apply a filter on the object key. 
                        if (cli.getParameter("filter") != null) {
                            Pattern pattern = Pattern.compile(cli.getParameter("filter"), Pattern.CASE_INSENSITIVE);
                            Matcher matcher = pattern.matcher(objectKey);
                            match = matcher.find();
                        }

                        //Identifies if should apply a filter on the object modified date.
                        if (cli.getParameter("start_date") != null
                                && cli.getParameter("end_date") != null) {

                            interval = updatedDate.compareTo(LocalDateTime.parse(cli.getParameter("start_date") + "T" + cli.getParameter("start_time"))) >= 0
                                    && updatedDate.compareTo(LocalDateTime.parse(cli.getParameter("end_date") + "T" + cli.getParameter("end_time"))) <= 0;
                        }
                    } else {
                        Logger.getLogger(S3.class.getName()).log(Level.SEVERE, "start_date and end_date or filter parameter should be supplied");
                        System.exit(1);
                    }

                    //Identifies if the file modification date is between start_date and end_date or if ir match an regexp filter.
                    if (interval && match) {
                        Logger.getLogger(S3.class.getName()).log(Level.INFO, "Transfering: {0} of {1} ({2})", new Object[]{s3ObjectSummary.getKey(), s3ObjectSummary.getLastModified(), FileUtils.byteCountToDisplaySize(s3ObjectSummary.getSize())});

                        //Identifies the output file. 
                        File outputFile = new File(outputPath.toString() + "/" + s3ObjectSummary.getKey().replaceAll("/", "_"));

                        //Transfer a file to local filesystem.               
                        TransferState transferState = downloadObject(
                                cli.getParameter("bucket"),
                                s3ObjectSummary.getKey(),
                                outputFile,
                                cli.getParameter("profile"),
                                cli.getParameter("region")
                        );

                        //Identifies if should retry.
                        if (!transferState.equals(TransferState.Completed)) {
                            for (int i = 0; i < cli.getParameterAsInteger("retries"); i++) {
                                transferState = downloadObject(
                                        cli.getParameter("bucket"),
                                        s3ObjectSummary.getKey(),
                                        outputFile,
                                        cli.getParameter("profile"),
                                        cli.getParameter("region")
                                );

                                if (transferState.equals(TransferState.Completed)) {
                                    break;
                                }
                            }

                            if (!transferState.equals(TransferState.Completed)) {
                                throw new AmazonClientException("Fail downloading object" + cli.getParameter("bucket") + "/" + s3ObjectSummary.getKey() + " with state " + transferState.name() + "!");
                            }
                        }

                        //Sets the same modified date as the original file.
                        outputFile.setLastModified(s3ObjectSummary.getLastModified().getTime());
                    }
                }
            }

            Logger.getLogger(S3.class.getName()).log(Level.INFO, "Writing output file to: {0}", cli.getParameter("output"));

            //Identifies if the input file has header. 
            if (cli.hasParameter("no_header")) {
                mitt.getWriterSettings().setHeader(
                        mitt
                                .getConfiguration()
                                .getOriginalFieldName());
            }

            //Writes the final file.
            mitt.getReaderSettings().setDelimiter(cli.getParameter("delimiter").charAt(0));

            if (cli.getParameter("properties") != null) {
                mitt.getReaderSettings().setProperties(cli.getParameter("properties"));
            }

            mitt.write(outputPath.toFile());

            //Remove temporary path. 
            Files.delete(outputPath);
        } catch (DuplicateEntityException
                | IOException
                | AmazonClientException
                | InterruptedException ex) {
            Logger.getLogger(S3.class.getName()).log(Level.SEVERE, "S3 Failure: ", ex);
            System.exit(1);
        } finally {
            mitt.close();
        }

        Logger.getLogger(S3.class.getName()).info("Glove - S3 Extractor finalized.");
    }

    /**
     * Download an object.
     *
     * @param bucket Bucket name.
     * @param object Object path name.
     * @param outputFile Output file.
     * @param profile String AWS credentials profile.
     * @param region AWS Region.
     * @return TransferState.
     * @throws InterruptedException
     */
    public static TransferState downloadObject(
            String bucket,
            String object,
            File outputFile,
            String profile,
            String region) throws AmazonServiceException, AmazonClientException, InterruptedException {

        TransferManager transferManager = TransferManagerBuilder
                .standard()
                .withS3Client(
                        AmazonS3ClientBuilder
                                .standard()
                                .withCredentials(new ProfileCredentialsProvider(profile))
                                .withRegion(region)
                                .build()
                ).build();

        Download download = transferManager.download(bucket, object, outputFile);
        download.waitForCompletion();
        transferManager.shutdownNow();
        return download.getState();
    }
}
