# Catalogue of Life Backend

For source code contributions please see our [developer guide](DEVELOPER-GUIDE.md).
The CoL backend consists of 2 [Dropwizard](https://www.dropwizard.io/) applications, colplus-ws and colplus-admin, that drive different parts of the [colplus API](http://api.col.plus). colplus-ws is responsible for most lightweight REST services exposing the Clearinghouse and the CoL. colplus-admin does the more heavyweight indexing of datasets and assembly of catalogues.


## Prerequisites
1. Java 8 JDK
1. Maven 3
1. Postgres 11

## Run the CoL WS application locally
1. cd into `colplus-ws`
1. Run `mvn clean install` to build your application
1. create a local [config.yml](colplus-ws/src/main/resources/config.yaml) file
1. On the first run init a new, empty database with `java -jar target/colplus-admin-1.0-SNAPSHOT.jar initdb config.yml`
1. Start application with `java -jar target/colplus-ws-1.0-SNAPSHOT.jar server config.yml`
1. To check that your application is running enter url `http://localhost:8080`

For development tests you can also run the application straight from your IDE 
by executing the main `WsServer.java` or `AdminServer.java` class and passing it the right arguments `server /path/to/config.yml`

In order to avoid real authentication against the GBIF registry you can change the AuthBundle and use a LocalAuthFilter
instead of the real AuthFilter. This authenticates every request with a test account with full admin privileges.


## Health Check
To see your applications health enter url `http://localhost:8081/healthcheck`


## Maven modules

### colplus-api
The main API with model classes and shared common utilities classes.

### colplus-dao
The postgres persistence layer.

### colplus-parser
Various parsers/interpreters used mostly for importing.
Contains a GBIF name parser wrapper.

### colplus-ws
The Dropwizard based JSON webservices, importer and assembly code.



## Dataset imports
The admin server should be used to import known datasets from their registered data access URL.
Imports are scheduled in an internal, non persistent queue. 
Scheduling a dataset for importing is done by POSTing an import request object to the importer resource like this:

```curl -X POST -d "{'datasetKey'=1000, 'priority'=false}" "http://localhost:8080/importer/queue"```

The priority parameter places the request on the beginning of the queue.


### Data Normalizer
All data is normalized prior to inserting it into the database.
This includes transforming a flat classification into a parent child hierarchy 
with just a single record for a uniue higher taxon.
 
### Import behaviour
We have built the importer to fail early when encountering issue to not overwrite existing good data.
Examples of data errors that cause the importer to abort are:
 
 - unreadable data files (we only support UTF8, 16 and Latin1, Windows1552 & MacRoman as 8bit encodings)
 - missing required fields (e.g. AcceptedSpeciesID or the scientific name)
 

The importer does gracefully handle empty lines and skip lines with less columns than expected 
(this shows as warning logs as bad delimiter escaping is often the root cause).

### Issue flagging
The dataset import flags records that have problems. 
For each entire dataset import aggregate metrics are stored and can be retrieved even for historic versions for comparison and change analytics.

All potential issues that are handled can be found here:
https://github.com/Sp2000/colplus-backend/blob/master/colplus-api/src/main/java/org/col/api/vocab/Issue.java#L6

For example:

 - declared accepted taxa are missing in the sources (e.g. a synonym declaring an AcceptedSpeciesID which does not exist)
