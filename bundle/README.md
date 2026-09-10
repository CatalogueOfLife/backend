# CLB release-in-a-box

A self contained Docker bundle serving **one** Catalogue of Life release: the read ChecklistBank API,
name matching and OpenRefine reconciliation, backed by its own Postgres and Elasticsearch. A COL
release also gets the **mini portal** in [`portal/`](portal) — a small COL website for browsing that
one release. See [`../docs/BUNDLE.md`](../docs/BUNDLE.md) for the full reference.

This directory holds only what is needed to **build and publish the images**. Everything a user runs —
`docker-compose.yml`, `config.yml`, `restore.sh`, a README — is generated into the data artifact by
`BundleBuildCmd` from the templates in
`webservice/src/main/resources/life/catalogue/bundle/`, with the release key already filled in.

| File | What |
|---|---|
| `Dockerfile` | the app image: the read API, matching and reconciliation |
| `Dockerfile.portal` + `nginx.conf` + `portal/` | the mini portal image: static pages and a proxy to the app |

## Publish the images

Neither image carries a release key, so one build of each serves any release — tag both with the
**backend version**, never the release, and keep the two tags identical.

```bash
mvn -DskipTests clean install

docker build -f bundle/Dockerfile        -t ghcr.io/catalogueoflife/clb-bundle:1.5.2 .
docker build -f bundle/Dockerfile.portal -t ghcr.io/catalogueoflife/clb-bundle-portal:1.5.2 .

for i in clb-bundle clb-bundle-portal; do
  docker tag  ghcr.io/catalogueoflife/$i:1.5.2 ghcr.io/catalogueoflife/$i:latest
  docker push ghcr.io/catalogueoflife/$i:1.5.2
  docker push ghcr.io/catalogueoflife/$i:latest
done
```

The portal image is static files only, so it needs no maven build — but `mvn install` is still
required for the app image's shaded jar.

## Build a data artifact

Runs against a full ChecklistBank database and needs `pg_dump` on the PATH.

```bash
java -cp webservice/target/webservice-*.jar life.catalogue.WsServer \
  bundleBuild --key 3287 --dir /srv/col-3287-bundle --delete \
  --image ghcr.io/catalogueoflife/clb-bundle:1.5.2 \
  --portal-image ghcr.io/catalogueoflife/clb-bundle-portal:1.5.2 config-prod.yml

tar -C /srv -caf col-3287-bundle.tar.zst col-3287-bundle
sha256sum col-3287-bundle.tar.zst > col-3287-bundle.tar.zst.sha256
```

Publish both files next to the other downloads of that release.

## Automate it

- `.github/workflows/bundle-image.yml` publishes both images on every `v*` tag, from one resolved
  version so they cannot drift apart.
- `Jenkinsfile` builds a data artifact from a single `RELEASE_KEY` parameter by driving
  `deploy/bundle.sh` on the apps VM.
- A `publishActions` entry in the project release config triggers that job when a release is
  published. See [`../docs/BUNDLE.md`](../docs/BUNDLE.md#automation).

## Run one

```bash
cd /srv/col-3287-bundle
docker compose up --wait
```

The API is on <http://localhost:8080>. For a COL release the mini portal is on
<http://localhost/> — set `CLB_PORTAL_PORT` if port 80 is taken. A bundle of any other project has
no `web` service at all; pass `--portal true` to `bundleBuild` to force one.

To test an artifact against a locally built image before publishing, layer the build override on top —
the artifact's compose file stays the single definition of the services:

```bash
cd /srv/col-3287-bundle
export CLB_BACKEND_DIR=/path/to/backend
docker compose -f docker-compose.yml -f $CLB_BACKEND_DIR/bundle/docker-compose.build.yml up --build --wait
```
