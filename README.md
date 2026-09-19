# ChecklistBank Backend

The ChecklistBank backend is a [Dropwizard](https://www.dropwizard.io/) application that drives the
[ChecklistBank API](https://api.checklistbank.org/). It holds almost all data, search, name matching, import,
release and sync logic of ChecklistBank and the Catalogue of Life.
`webservice` is the maven module that builds the application. The API is documented at
[checklistbank.org/about/API](https://www.checklistbank.org/about/API).

Related repositories:

- [CatalogueOfLife/checklistbank](https://github.com/CatalogueOfLife/checklistbank) - the frontend for [checklistbank.org](https://www.checklistbank.org)
- [CatalogueOfLife/portal](https://github.com/CatalogueOfLife/portal) - the [catalogueoflife.org](https://www.catalogueoflife.org) website
- [CatalogueOfLife/portal-components](https://github.com/CatalogueOfLife/portal-components) - embeddable tree, search and taxon components
- [CatalogueOfLife/coldp](https://github.com/CatalogueOfLife/coldp) - the COL Data Package format

For source code contributions please see our [developer guide](DEVELOPER-GUIDE.md).


## Prerequisites
1. Java 25 JDK
1. Maven 3.9.5 or later
1. Postgres 17
1. Elasticsearch 9, optional - without it the name usage search is not available
1. Docker, for the integration tests which use [Testcontainers](https://testcontainers.com/)


## Build & test
```bash
mvn clean install               # build everything and run all tests
mvn clean install -DskipTests   # build only
mvn test                        # unit tests (*Test)
mvn verify                      # also the integration tests (*IT), which need Docker
```


## Run the application locally
1. Build the project with `mvn clean install -DskipTests`
1. cd into `webservice` and create a local `config.yml`. [config-local.yaml](webservice/config-local.yaml) is a good starting point
1. On the first run create a new, empty database & search index with `java -jar target/webservice-*-SNAPSHOT.jar init --num 4 config.yml`.
   `--num` sets the number of hash partitions for the data tables
1. Start the application with `java -jar target/webservice-*-SNAPSHOT.jar server config.yml`
1. Start the background components as an admin with `curl -X POST -u admin:<password> http://localhost:8080/admin/component/start-all`.
   Dropwizard does not start them by itself - the names index, the job executor and the schedulers stay off until you do
1. Check that the application is running at `http://localhost:8080`

For development you can also run the application straight from your IDE
by executing the main `WsServer.java` class and passing it the arguments `server /path/to/config.yml`.

To avoid real authentication against the GBIF registry configure the `map` authentication in your config,
which authenticates against a fixed list of users and roles:

```yaml
auth:
  type: map
  users:
    - username: admin
      password: admin
      role: admin
```

The jar also contains a number of CLI commands, e.g. `index` to rebuild the search index, `nidx` to rebuild the names index
or `export` to export a dataset. Run `java -jar target/webservice-*-SNAPSHOT.jar -h` to list them all.


### Servers
The same jar provides several Dropwizard applications:

| Main class | Purpose |
|---|---|
| `WsServer` | The main read/write server with imports, syncs, releases and exports. The jar's default main class |
| `WsROServer` | A read-only server for the public API, run next to `WsServer` in production |
| `WsMatchingServer` | A standalone name matching service for a single dataset, see the `matchingServerBuild` command |
| `WsBundleServer` | Serves a single release from its own Postgres & Elasticsearch, see [BUNDLE.md](docs/BUNDLE.md) |

Start any but the default one with `java -cp target/webservice-*-SNAPSHOT.jar life.catalogue.WsROServer server config.yml`.


## Health Check
To see your application's health open the admin port at `http://localhost:8081/healthcheck`.


## Maven modules

| Module | Content |
|---|---|
| `api` | The main API with model classes and shared common utility classes |
| `vocab` | Controlled vocabularies used by the API |
| `coldp` | Terms of the [ColDP](https://github.com/CatalogueOfLife/coldp) format with minimal dependencies |
| `parser` | Parsers for enumerations and other controlled vocabularies, including a GBIF name parser wrapper |
| `reader` | CSV readers for DwC-A, ColDP and ACEF |
| `reader-xls` | Excel spreadsheet support for DwC-A, ColDP and ACEF |
| `metadata` | Maps dataset metadata formats like ColDP and EML to the API |
| `reference` | Citation formatting (CSL/citeproc, BibTeX) |
| `kryo` | Kryo binary serialisation of the API model classes |
| `pgcopy` | Reading and writing the Postgres binary copy format |
| `doi` | DOI registration and management in DataCite |
| `dao` | The Postgres persistence layer with MyBatis mappers, Elasticsearch and the names index |
| `core` | Business logic not tied to the webservice: assembly & sector sync, releases, exports, matching and jobs |
| `importer` | Dataset imports and normalisation for ColDP, DwC-A, ACEF and text trees |
| `webservice` | The Dropwizard applications, JSON resources and CLI commands |

The [bundle](bundle) folder holds the Docker images of the single release "CLB in a box".


## Documentation
Further documentation lives in [docs](docs). Files with an ALL-CAPS name describe the current behaviour,
e.g. [XRELEASE.md](docs/XRELEASE.md), [HIERARCHY-SYNC.md](docs/HIERARCHY-SYNC.md), [DOI.md](docs/DOI.md) or [OPENREFINE.md](docs/OPENREFINE.md).
Dated files are historical design records of individual changes and are not kept up to date.
