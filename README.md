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

### colplus-cli
Command line tools including tools to normalize and load in Darin Core archives.

### colplus-common
Shared common classes like utilities.

### colplus-dao
The postgres persistence layer.

### colplus-dwca
An extension to dwca-io for dealing with CoL+ Darwin Core archives containing extra normalized data about references.

### colplus-ws
The Dropwizard based JSON webservices.


