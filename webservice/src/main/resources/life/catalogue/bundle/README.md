# {{TITLE}}

A self contained, offline copy of one Catalogue of Life release — release key **{{RELEASE_KEY}}**,
built {{BUILT}}. It serves the read ChecklistBank API, name matching and OpenRefine reconciliation
from its own Postgres and Elasticsearch. No network access is needed once the images are pulled.

```bash
docker compose up --wait
```

The first start restores the Postgres dump and indexes the release into Elasticsearch, which takes a
while. `--wait` blocks until that is done; later starts are immediate.

```bash
curl localhost:8080/dataset                      # the shipped release
curl localhost:8080/taxon/{id}/info              # no dataset key needed
curl localhost:8080/tree
curl 'localhost:8080/nameusage/search?q=Abies'
curl 'localhost:8080/parser/name?q=Abies+alba'
curl localhost:8080/reconcile                    # OpenRefine service manifest
curl 'localhost:8080/match/nameusage?q=Puma+concolor'
```

In OpenRefine, add `http://localhost:8080/reconcile` as a standard reconciliation service.

The keyed forms (`/dataset/{{RELEASE_KEY}}/taxon/{id}/info`) work too, so code written against
`api.checklistbank.org` runs unchanged against this bundle.

## What is in here

| File | |
|---|---|
| `release.dump` | `pg_dump -Fc` of a database holding just this release |
| `nidx/` | names index store |
| `matcher/{{RELEASE_KEY}}/` | memory mapped usage matcher store |
| `metrics/` | file based dataset metrics |
| `bundle.json` | release key, title, attempt, build time, source |
| `config.yml` | the app config, release key already filled in |
| `docker-compose.yml` | the three services |
| `restore.sh` | Postgres first boot restore hook |

This directory is mounted read-write: the names index grows as new names are matched.

To run a self built image instead of the published one, set `CLB_BUNDLE_IMAGE`.
Full reference: <https://github.com/CatalogueOfLife/backend/blob/master/docs/BUNDLE.md>
