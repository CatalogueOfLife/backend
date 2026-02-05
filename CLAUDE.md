# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Catalogue of Life (CoL) Backend - A Dropwizard-based Java 21 application providing RESTful JSON webservices for ChecklistBank API (https://api.checklistbank.org/). 
Manages taxonomic datasets with import, normalization, assembly, search indexing, and release management capabilities.

## Build & Test Commands

### Building
```bash
# Build entire project
mvn clean install

# Build specific module
cd webservice && mvn clean install

# Skip tests
mvn clean install -DskipTests

# Build with dependency updates check
mvn versions:display-dependency-updates
```

### Testing
```bash
# Run all tests
mvn test

# Run integration tests
mvn verify

# Run specific test
mvn test -Dtest=ClassName#methodName

# Run tests in a specific module
cd dao && mvn test
```

### Running the Application
```bash
# Initialize database (first time only, --num sets partition count)
cd webservice
java -jar target/webservice-1.0-SNAPSHOT.jar init --num 4 config.yml

# Start webservice
java -jar target/webservice-1.0-SNAPSHOT.jar server config.yml

# Run from IDE: Execute WsServer.main() with args: server /path/to/config.yml
```

### Useful Commands
```bash
# Check dependency conflicts
mvn dependency:tree

# Generate module dependency graph
mvn -P modgraph

# Check for newer dependency versions
mvn versions:display-dependency-updates

# Run dataset import via API
curl -X POST -d "{'datasetKey':1000, 'priority':false}" "http://localhost:8080/importer/queue"
```

## Architecture

### Multi-Module Maven Project Structure

The project consists of 13 Maven modules with clear dependency relationships:

```
api → dao → core → importer → webservice
  ↑     ↑     ↑        ↑
parser, coldp, reader, reader-xls, pgcopy, metadata, doi, matching-ws
```

**Module Responsibilities:**

- **api**: Data models (Dataset, NameUsage, Taxon, Synonym, Reference), vocabularies (TaxonomicStatus, Issue, ImportState), validation annotations, Jackson serializers. No external dependencies beyond basic libraries.

- **dao**: Persistence layer with MyBatis mappers (`dao/src/main/resources/mapper/*.xml`), DAO classes (`dao/dao/*.java`), PostgreSQL utilities, Elasticsearch integration, name matching algorithms. Uses **dataset-based table partitioning** for performance.

- **core**: Business logic not tied to HTTP layer. Key packages:
  - `assembly/`: Sector synchronization (merges source datasets into projects via SectorSync)
  - `exporter/`: Export to COLDP/DwC-A formats
  - `release/`: Project versioning and release management
  - `jobs/`: Background task execution with CronExecutor
  - `matching/`: Taxonomic name matching algorithms

- **importer**: Dataset import orchestration:
  - Format-specific parsers: `coldp/`, `dwca/`, `acef/`, `txttree/`
  - `ImportManager`: Priority-queue based import scheduler
  - `Normalizer`: Transforms flat data into parent-child hierarchies
  - Fail-early strategy: aborts on critical errors, logs issues for minor problems

- **webservice**: Dropwizard application entry point
  - `WsServer.java`: Main application class
  - `resources/`: Jersey REST endpoints (DatasetResource, TaxonResource, etc.)
  - `command/`: CLI commands (init, index, export, partition)
  - `dw/`: Dropwizard bundles (auth, CORS, mail, etc.)

- **parser**: GBIF name parser wrapper, term interpretation
- **reader** / **reader-xls**: CSV and Excel file parsing
- **coldp**: COL Data Package (COLDP) term definitions
- **metadata**: Dataset metadata handling (EML XML, DataCite)
- **doi**: DOI registration with DataCite
- **pgcopy**: PostgreSQL COPY command utilities for bulk loading
- **matching-ws**: Standalone Spring Boot name matching service with Lucene indexes

### Data Flow: Import Pipeline

```
1. POST /importer/queue → ImportManager.importDataset()
2. ImportJob.run() (async in PBQThreadPoolExecutor)
   ├─ Download source data from configured URL
   ├─ Detect format (COLDP/DWCA/ACEF/TXT-Tree)
   ├─ Parse using format-specific parser
   ├─ Normalize: flat CSV → parent-child hierarchy
   ├─ Validate (fail-early on critical errors)
   ├─ Insert into PostgreSQL (MyBatis + pgcopy bulk insert)
   ├─ Index in Elasticsearch
   └─ Emit DatasetChanged event
3. GET /dataset/{key}/nameusage retrieves via DAO layer
```

### Key Architectural Patterns

**Compound Keys (DSID Pattern):**
Records are unique only within a dataset. Combined key = `datasetKey + id`. Enables multi-dataset system while preserving source IDs.

**Dataset Partitioning:**
- EXTERNAL datasets → shared default partition
- PROJECT/RELEASE datasets → dedicated partitions (created with `--num` during init)
- Avoids expensive full-table scans when querying single datasets

**Event-Driven Updates:**
`EventBroker` publishes `DatasetChanged` events. Services subscribe to invalidate caches, rebuild indices, etc. Decouples import pipeline from downstream updates.

**DAO Pattern:**
All DAOs extend `DataEntityDao<Key, Entity, Mapper>`:
- MyBatis XML mappers in `dao/src/main/resources/mapper/*.xml`
- Java interfaces in `dao/db/mapper/*Mapper.java`
- CRUD operations: `get()`, `create()`, `update()`, `delete()`, `search()`

**Sector Synchronization (Assembly):**
`SectorSync` in core module merges portions of source datasets into managed projects. A "sector" defines which subtree from a source dataset contributes to a project. The sync process:
1. Identifies source taxa in sector
2. Matches against existing project taxa
3. Inserts/updates/deletes to synchronize
4. Rebuilds name index

**Name Index:**
FastUtil-based in-memory index for rapid taxonomic name matching. Rebuilt from database on startup. Used during imports and sector synchronization.

## Development Guidelines

### Code Style
Follows Twitter Commons style guide with CoL customizations:
- **Indent**: 2 spaces (1TBS brace style)
- **Line limit**: 140 columns
- **Naming**: CamelCase types, camelCase variables, UPPER_SNAKE constants
- **Java version**: Java 21 (required)
- **Annotations**: Use `@Nullable` for nullable parameters/fields, `@VisibleForTesting` for test-only visibility
- See `DEVELOPER-GUIDE.md` for comprehensive style guide

### Testing Conventions
- **Unit tests** (`*Test.java`): Use Mockito, no database, run in parallel
- **Integration tests** (`*IT.java`): Use TestContainers (PostgreSQL/Elasticsearch), run sequentially in separate JVM forks
- Test resources in `src/test/resources/`
- `api` module publishes `tests` classifier JAR with test utilities

### MyBatis Mappers
- XML mappers in `dao/src/main/resources/mapper/*.xml`
- Java interfaces in `dao/db/mapper/*.java`
- Use `<sql id="...">` fragments for reusable SQL blocks
- Dynamic SQL with `<if>`, `<choose>`, `<foreach>`

### Adding a New Entity Type
1. Create model class in `api/src/main/java/life/catalogue/api/model/`
2. Create MyBatis mapper XML in `dao/src/main/resources/mapper/`
3. Create MyBatis mapper interface in `dao/db/mapper/`
4. Create DAO in `dao/dao/`
5. Create REST resource in `webservice/resources/`
6. Add tests in all affected modules

### Working with Imports
- Format-specific parsers in `importer/{format}/` (coldp, dwca, acef, txttree)
- Add test data in `importer/src/test/resources/{format}/`
- Normalizer handles flat → hierarchical conversion
- Issue flagging in `api/vocab/Issue.java` - add new issue types as needed

### Database Schema Changes
1. Create Liquibase changeset in `dao/src/main/resources/liquibase/`
2. Update MyBatis mappers
3. Update entity models if columns changed
4. Run `init` command to apply changes
5. Add migration tests

### Dropwizard Patterns
- **Bundles** (registered in `WsServer.initialize()`): Configure cross-cutting concerns (auth, CORS, database, etc.)
- **Resources** (Jersey endpoints): Use `@Path`, `@GET/@POST/@PUT/@DELETE`, extend `AbstractDatasetResource` or `AbstractGlobalResource`
- **Commands** (CLI): Implement `EnvironmentCommand` or `ConfiguredCommand`, register in `WsServer`
- **Health Checks**: Implement `HealthCheck`, monitor at `/healthcheck` admin endpoint

### Configuration
- Main config: `webservice/src/main/resources/config.yaml` (Dropwizard YAML format)
- Local development: Create `config-local.yml`, use `LocalAuthFilter` to bypass GBIF registry authentication
- Environment-specific configs can override with Java properties

### Debugging Tips
- Enable MyBatis logging: Set `org.apache.ibatis` logger to DEBUG in config
- Enable app logging: Set `life.catalogue` logger to DEBUG
- Use IDE debugger with `WsServer` main class, args: `server /path/to/config.yml`
- Check admin endpoints: `http://localhost:8081/healthcheck`, `http://localhost:8081/metrics`
- Slow queries: Use `EXPLAIN ANALYZE` in PostgreSQL

## Common Tasks

### Importing a Dataset
```bash
# Via API
curl -X POST -H "Content-Type: application/json" \
  -d '{"datasetKey": 1000, "priority": false}' \
  "http://localhost:8080/importer/queue"

# Via CLI (rare, usually done via API)
# Imports are normally triggered through the web API
```

### Rebuilding Name Index
```bash
java -jar target/webservice-1.0-SNAPSHOT.jar index config.yml
```

### Creating a Dataset Release
Releases are versioned snapshots of managed projects. Typically done via API:
```bash
POST /dataset/{projectKey}/release
```

### Exporting a Dataset
```bash
# Via CLI
java -jar target/webservice-1.0-SNAPSHOT.jar export \
  --format COLDP --dataset-key 1000 --output /path/to/export config.yml

# Via API
GET /dataset/{key}/export.zip?format=COLDP
```

### Running in Docker
See `matching-ws/README.md` for Docker build examples. Main webservice typically runs via systemd/supervisor, not Docker in production.

## Important Files

| Purpose | Path |
|---------|------|
| Main application | `webservice/src/main/java/life/catalogue/WsServer.java` |
| Configuration | `webservice/src/main/resources/config.yaml` |
| API models | `api/src/main/java/life/catalogue/api/model/` |
| Vocabularies | `api/src/main/java/life/catalogue/api/vocab/` |
| DAOs | `dao/src/main/java/life/catalogue/dao/` |
| MyBatis mappers | `dao/src/main/resources/mapper/` |
| Import logic | `importer/src/main/java/life/catalogue/importer/` |
| REST endpoints | `webservice/src/main/java/life/catalogue/resources/` |
| Assembly/Sync | `core/src/main/java/life/catalogue/assembly/` |
| Database schema | `dao/src/main/resources/liquibase/` |

## Prerequisites

- Java 21 JDK
- Maven 3.9.5+
- PostgreSQL 17
- Elasticsearch 8.1.3 (optional, for name usage search)
- Docker (for TestContainers in integration tests)

## External Dependencies

- **Dropwizard 5.0.0**: Web framework (includes Jersey, Jackson, Jetty, Metrics)
- **MyBatis 3.5.19**: SQL persistence
- **GBIF libraries**: name-parser, dwc-api, dwca-io (taxonomic name parsing and Darwin Core support)
- **Elasticsearch 8.1.3**: Name usage search indexing
- See `pom.xml` for full dependency list (Dropwizard BOM manages most transitive versions)

## References

- [Dropwizard Documentation](https://www.dropwizard.io/en/release-5.0.x/)
- [MyBatis Documentation](http://www.mybatis.org/mybatis-3/)
- [GBIF Name Parser](https://github.com/gbif/name-parser)
- Developer guide: `DEVELOPER-GUIDE.md`
- API documentation: `API.md`
