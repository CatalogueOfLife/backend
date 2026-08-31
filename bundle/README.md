# CLB release-in-a-box

A self contained Docker bundle serving **one** Catalogue of Life release: the read ChecklistBank API,
name matching and OpenRefine reconciliation, backed by its own Postgres and Elasticsearch.
See [`../docs/BUNDLE.md`](../docs/BUNDLE.md) for the full reference.

```bash
# 1. build the data artifact from a full ChecklistBank database (needs pg_dump on the PATH)
java -cp webservice/target/webservice-*.jar life.catalogue.WsServer \
  bundleBuild --key 3287 --dir /srv/bundle-data config-prod.yml

# 2. set the release key the bundle serves
$EDITOR bundle/config-bundle.yml     # releaseKey: 3287

# 3. run it
cd bundle
BUNDLE_DATA=/srv/bundle-data docker compose up --wait

# 4. use it - no dataset key needed
curl localhost:8080/dataset
curl localhost:8080/taxon/4QHKG/info
curl 'localhost:8080/parser/name?q=Abies+alba'
curl localhost:8080/reconcile
```

The first boot restores the Postgres dump and indexes the release into Elasticsearch, which takes a
while for a full COL release. `docker compose up --wait` blocks until that is done.
