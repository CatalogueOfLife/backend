# CLB release-in-a-box

A self contained Docker bundle serving **one** Catalogue of Life release: the read ChecklistBank API,
name matching and OpenRefine reconciliation, backed by its own Postgres and Elasticsearch.
See [`../docs/BUNDLE.md`](../docs/BUNDLE.md) for the full reference.

This directory holds only what is needed to **build and publish the image**. Everything a user runs —
`docker-compose.yml`, `config.yml`, `restore.sh`, a README — is generated into the data artifact by
`BundleBuildCmd` from the templates in
`webservice/src/main/resources/life/catalogue/bundle/`, with the release key already filled in.

## Publish the image

One image serves any release, so tag it with the **backend version**, not the release.

```bash
mvn -DskipTests clean install
docker build -f bundle/Dockerfile -t ghcr.io/catalogueoflife/clb-bundle:1.5.2 .
docker tag ghcr.io/catalogueoflife/clb-bundle:1.5.2 ghcr.io/catalogueoflife/clb-bundle:latest
docker push ghcr.io/catalogueoflife/clb-bundle:1.5.2
docker push ghcr.io/catalogueoflife/clb-bundle:latest
```

## Build a data artifact

Runs against a full ChecklistBank database and needs `pg_dump` on the PATH.

```bash
java -cp webservice/target/webservice-*.jar life.catalogue.WsServer \
  bundleBuild --key 3287 --dir /srv/bundle-data --delete \
  --image ghcr.io/catalogueoflife/clb-bundle:1.5.2 config-prod.yml

tar -C /srv -caf col-3287-bundle.tar.zst bundle-data
sha256sum col-3287-bundle.tar.zst > col-3287-bundle.tar.zst.sha256
```

Publish both files next to the other downloads of that release.

## Automate it

- `.github/workflows/bundle-image.yml` publishes the image on every `v*` tag.
- `Jenkinsfile` builds a data artifact from a single `RELEASE_KEY` parameter by driving
  `deploy/bundle.sh` on the apps VM.
- A `publishActions` entry in the project release config triggers that job when a release is
  published. See [`../docs/BUNDLE.md`](../docs/BUNDLE.md#automation).

## Run one

```bash
cd /srv/bundle-data
docker compose up --wait
```

To test an artifact against a locally built image before publishing, layer the build override on top —
the artifact's compose file stays the single definition of the services:

```bash
cd /srv/bundle-data
export CLB_BACKEND_DIR=/path/to/backend
docker compose -f docker-compose.yml -f $CLB_BACKEND_DIR/bundle/docker-compose.build.yml up --build --wait
```
