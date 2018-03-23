# signedurl - This is a sample app that shows how to interact with GCS using signed URLS.
## Example walks through two scenarios. 
## 1. Read using signed URL 
### reference: https://cloud.google.com/storage/docs/access-control/create-signed-urls-program
In this step, first I create a bucket and file on GCS.
Once file is created, I create signed URL that can be used by clients.
This signed url has an expiration time. I set it as 1 minute.

## 2. Write using signed URL and resumable uploads 
### reference: https://cloud.google.com/storage/docs/xml-api/resumable-upload
This is a two step process. First we initiate a request(using POST method) to create file(blob). 
Once request is initiated, send URL to client so that client can upload data(using PUT method) to URL.


#### Things to be changed/considered for using this sample code:
1. Create your own bucket and use that name.
2. Customize your code for authentication. I am using service account key file. More details below.
3. THIS IS A SAMPLE CODE---- DON'T USE IT FOR PRODUCTION.

#### Please note: 
For authentication I am using service account key file. However, there are many options.
Here's a link that provides these options: https://cloud.google.com/docs/authentication/