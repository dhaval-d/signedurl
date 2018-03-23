/**
 * Copyright 2017 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */



import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.storage.StorageException;
import com.google.cloud.storage.StorageOptions;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.BucketInfo;
import org.joda.time.DateTime;

import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.net.*;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import com.google.api.services.storage.StorageScopes;
import java.util.Collections;


/** This sample app shows how to use signedURLs to read and write objects from clients.
 *  Signed URL : https://cloud.google.com/storage/docs/access-control/create-signed-urls-program
 *  Resumable Uploads : https://cloud.google.com/storage/docs/xml-api/resumable-upload
 * */
public class SampleApp {

    final static String BASE_GCS_URL = "storage.googleapis.com";
    final static String BUCKET_NAME = "dd_test_bucket_gcp_trial";
    final static String READ_FILE_NAME = "sample_read_signedURL.txt";
    final static String WRITE_FILE_NAME = "sample_write_client_uploads.txt";
    final static String READ_RESOURCE_URL = BASE_GCS_URL+"/"+BUCKET_NAME+"/"+READ_FILE_NAME;


    /** Entry point for this sample code.*/
    public static void main(String[] args){
        // Set Url expiry to one minute from now!
        String EXPIRATION_TIME = setExpirationTimeInEpoch();


        // Step 1 - Create sample file and assign SignedURL for 1 minute
        String signedURL = createSignedURL(EXPIRATION_TIME);
        // Step 2 - use this signed URL until expiration time (1 minute in this case) to read file
        System.out.println(signedURL);


        //Take some rest of 20 seconds before looking at resumable uploads
        try {
            System.out.println("Sleeping for 20 seconds.");
            Thread.sleep(20000);
        } catch(Exception ex){
        }


        //Two step process to allow resumable uploads
        //Step 1 - Make a POST request to initiate an upload
        String returnedURL = initiateUploadRequest(EXPIRATION_TIME);

        //Step 2 - If step-1 is fine and returns URL, send file data.
        //This step can be performed by clients. URL we receive already has accessToken so no need for signed URL.
        if(!returnedURL.equalsIgnoreCase("")){
            performClientDataUpload(returnedURL);
        }
    }

    /** This method create file in the bucket and creates signed URL that can be used to read until
     *  expiration time finishes.*/
    private static String createSignedURL(String EXPIRATION_TIME) {
        ServiceAccountCredentials credentials=null;
        File credentialsPath = new File("storage_writer.json");

        try {
            FileInputStream serviceAccountStream = new FileInputStream(credentialsPath);
            credentials = ServiceAccountCredentials.fromStream(serviceAccountStream);
        } catch (IOException ex){
            System.out.println("Some issue with service account file: "+ ex.getStackTrace());
        }

        Storage storage = StorageOptions.newBuilder()
                .setCredentials(credentials)
                .build()
                .getService();

        // Create a bucket
        try {
            Bucket bucket = storage.create(BucketInfo.of(BUCKET_NAME));
        }catch(StorageException ex){
            System.out.println("Some issue with bucket creation: "+ ex.getStackTrace());
        }
        // Upload a blob to the newly created bucket
        BlobId blobId = BlobId.of(BUCKET_NAME, READ_FILE_NAME);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType("text/plain").build();

        try {
            Blob blob = storage.create(blobInfo, "sample signed url demo.".getBytes());
        } catch(StorageException ex){
            System.out.println("Some issue with file creation: "+ ex.getStackTrace());
        }

        String requestString = "";
        requestString+= "GET" + "\n"
                + "\n"
                + "\n"
                + EXPIRATION_TIME + "\n"
                + "/"+BUCKET_NAME+"/"+READ_FILE_NAME;
        String GOOGLE_ACCESS_STORAGE_ID = credentials.getClientEmail();
        byte[] signature= credentials.sign(requestString.getBytes(StandardCharsets.UTF_8));
        String encoded_signature = Base64.getEncoder().encodeToString(signature);

        try {
            encoded_signature = URLEncoder.encode(encoded_signature, StandardCharsets.UTF_8.toString());
        }catch (Exception ex){

        }

        String final_signed_url = "https://" + READ_RESOURCE_URL + "?GoogleAccessId=" + GOOGLE_ACCESS_STORAGE_ID + "&Expires="
                + EXPIRATION_TIME + "&Signature=" + encoded_signature;
        return final_signed_url;
    }


    /** This method performs a first initiation step for file to be uploaded by client */
    private static String initiateUploadRequest(String EXPIRATION_TIME){
        GoogleCredentials credentials=null;
        File credentialsPath = new File("storage_writer.json");
        GoogleCredential gc=null;
        String returnedURL="";

        try {
            FileInputStream serviceAccountStream = new FileInputStream(credentialsPath);
            gc = GoogleCredential.fromStream(serviceAccountStream).createScoped(Collections.singleton(StorageScopes.DEVSTORAGE_READ_WRITE));
            gc.refreshToken();
        } catch (IOException ex){
            System.out.println("Some issue with service account file: "+ ex.getMessage());
        }

        // Needed to overcome SSL issue I was running into.
        javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier(
                new javax.net.ssl.HostnameVerifier(){
                    public boolean verify(String hostname,
                                          javax.net.ssl.SSLSession sslSession) {
                        return hostname.equals(BUCKET_NAME+"."+BASE_GCS_URL);
                    }
                });

        HttpURLConnection connection = null;
        try {
            //Create connection
            URL url = new URL("https://"+BASE_GCS_URL+"/"+BUCKET_NAME+"/"+WRITE_FILE_NAME);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Host", BUCKET_NAME+"."+BASE_GCS_URL);
            connection.setRequestProperty("Date", DateTime.now().toString());
            connection.setRequestProperty("Content-Length", Integer.toString(0));
            connection.setRequestProperty("Content-Type", "text/plain");
            connection.setRequestProperty("x-goog-resumable", "start");
            connection.setRequestProperty("Authorization", "Bearer "+gc.getAccessToken());
            connection.setDoOutput(true);

            //Send request
            DataOutputStream wr = new DataOutputStream (
                    connection.getOutputStream());
            String urlParameters="";
            wr.writeBytes(urlParameters);
            wr.flush();
            wr.close();
            System.out.println("Initiation step response code: "+connection.getResponseCode());
            returnedURL = connection.getHeaderField("Location");
            System.out.println(returnedURL);

            //Get Response
            InputStream is = connection.getInputStream();
            BufferedReader rd = new BufferedReader(new InputStreamReader(is));

            StringBuilder response = new StringBuilder(""); // or StringBuffer if Java version 5+
            String line;
            while ((line = rd.readLine()) != null) {
                System.out.println(line);
                response.append(line);
                response.append('\r');
            }
            rd.close();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return returnedURL;
    }


    /** This method emulates what a client can do with URL provided by step 1 to upload data. */
    private static void performClientDataUpload(String UPLOAD_URL){
        HttpURLConnection connection = null;
        String input_file_data = "This is resumable upload demo.";
        try {
            //Create connection
            URL url = new URL(UPLOAD_URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("PUT");
            connection.setRequestProperty("Host", BUCKET_NAME+"."+BASE_GCS_URL);
            connection.setRequestProperty("Date", DateTime.now().toString());
            connection.setRequestProperty("Content-Length"
                    , Integer.toString(input_file_data.getBytes(StandardCharsets.UTF_8.toString()).length));
            connection.setRequestProperty("Content-Type", "text/plain");
            connection.setDoOutput(true);

            OutputStreamWriter out = new OutputStreamWriter(
                    connection.getOutputStream());
            out.write(input_file_data);
            out.close();
            connection.getInputStream();

            System.out.println("Client data upload response code: "+connection.getResponseCode());
            //Get Response
            InputStream is = connection.getInputStream();
            BufferedReader rd = new BufferedReader(new InputStreamReader(is));
            StringBuilder response = new StringBuilder(""); // or StringBuffer if Java version 5+
            String line;
            while ((line = rd.readLine()) != null) {
                System.out.println(line);
                response.append(line);
                response.append('\r');
            }
            rd.close();
       } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }


    /** Set an expiry date for the signed url. Sets it at one minute ahead of current time.
     *  Represented as the epoch time (seconds since 1st January 1970) */
    private static String setExpirationTimeInEpoch() {
        long now = System.currentTimeMillis();
        String expirationTime="";
        // expire in a minute!
        // note the conversion to seconds as needed by GCS.
        long expiredTimeInSeconds = (now + 60 * 1000L) / 1000;
        expirationTime = expiredTimeInSeconds + "";
        return expirationTime;

    }

}
