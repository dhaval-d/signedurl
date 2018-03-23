# signedurl - This is a sample app that shows how to interact with GCS using signed URLS.
## Example walks through two scenarios. 
## 1. Read using signed URL 
### reference: https://cloud.google.com/storage/docs/access-control/create-signed-urls-program
In this step, first I create a bucket and file on GCS.
Once file is created, I create signed URL that can be used by clients.
This signed url has an expiration time. I set it as 1 minute.

## 2. Write using signed URL and resumable uploads 
### reference: https://cloud.google.com/storage/docs/xml-api/resumable-upload
This is a two step process. First we initiate request. 
Once request is initiated, send URL to client so that client can upload data to URL.