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

Neither image carries a release key, so one build of each serves any release — both are tagged with
the **backend version**, never the release, and the two tags are always identical.

[`.github/workflows/bundle-image.yml`](../.github/workflows/bundle-image.yml) publishes both to
`ghcr.io/catalogueoflife/` on every `v*` tag and moves `:latest`, so a backend release needs nothing
by hand. To rebuild an existing release, run the workflow from master with its version — it builds the
git tag `v<version>`, not master:

```bash
gh workflow run bundle-image.yml --ref master -f tag=1.5.3 -f latest=false
```

## Build a data artifact

Runs against a full ChecklistBank database and needs `pg_dump` on the PATH.

```bash
java -cp webservice/target/webservice-*.jar life.catalogue.WsServer \
  bundleBuild --key 3287 --dir /srv/col-3287-bundle --delete \
  --image ghcr.io/catalogueoflife/clb-bundle:1.5.3 \
  --portal-image ghcr.io/catalogueoflife/clb-bundle-portal:1.5.3 config-prod.yml

tar -C /srv -caf col-3287-bundle.tar.zst col-3287-bundle
sha256sum col-3287-bundle.tar.zst > col-3287-bundle.tar.zst.sha256
```

Publish both files next to the other downloads of that release.

## Automate it

- `.github/workflows/bundle-image.yml` publishes both images, see [above](#publish-the-images).
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
