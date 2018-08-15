# Catalogue of Life Backend

For source code contributions please see our [developer guide](DEVELOPER-GUIDE.md)


## Prerequisites
1. Java 8 JDK
1. Maven 3
1. Postgres 9.6 or later.

## Run the CoL application locally
1. create a local [config.yml](config.yml) file
1. Run `mvn clean install` to build your application
1. On the first run init a new, empty database with `java -jar target/colplus-backend-1.0-SNAPSHOT.jar initdb config.yml`
1. Start application with `java -jar target/colplus-backend-1.0-SNAPSHOT.jar server config.yml`
1. To check that your application is running enter url `http://localhost:8080`

For development tests you can also run the application straight from your IDE 
by executing the main `ColApp.java` class and passing it the right arguments `server /path/to/config.yml`

## Health Check
To see your applications health enter url `http://localhost:8081/healthcheck`



## Maven modules

### colplus-api
The main API with model classes.

### colplus-admin
Admin server which allows to load ACEF and Darwin Core Archive datasets, sync with the GBIF registry and more.

### colplus-common
Shared common classes like utilities.

### colplus-dao
The postgres persistence layer.

### colplus-ws
The Dropwizard based JSON webservices.


## Dataset imports
The admin server should be used to import known datasets from their registered data access URL.
Imports are scheduled in an internal, non persistent queue. 
Scheduling a dataset for importing is done by POSTing a dataset key to the importer resource like this:

```curl -X POST "http://localhost/importer?force=true&key=$1"```

The force parameter overrides the default behavior to stop importing if the exact same archive has been imported before.

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
