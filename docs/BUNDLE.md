# CLB release-in-a-box bundle

A self contained Docker bundle that serves **one** Catalogue of Life release: the read ChecklistBank
API, name matching and OpenRefine reconciliation, backed by its own Postgres and its own
Elasticsearch. It exists for offline R-client and OpenRefine users and doubles as a relocatable
matching tier. A COL release additionally gets a **mini portal** on port 80 - a small COL website
for browsing that one release, see [The mini portal](#the-mini-portal).

It supersedes nothing: the DB-free single dataset `WsMatchingServer` still exists and still serves
matching only. The bundle is the fuller thing — it has a database and therefore reuses every read
resource unchanged.

The design record behind it is [`2026-06-20-clb-release-in-a-box-bundle.md`](2026-06-20-clb-release-in-a-box-bundle.md).

## Parts

| Piece | Where |
|---|---|
| `WsBundleServer` | `webservice/src/main/java/life/catalogue/WsBundleServer.java` |
| `WsBundleServerConfig` | `webservice/src/main/java/life/catalogue/WsBundleServerConfig.java` |
| keyless routing | `webservice/src/main/java/life/catalogue/dw/jersey/filter/SingleDatasetRewriteFilter.java` |
| data artifact builder | `webservice/src/main/java/life/catalogue/command/BundleBuildCmd.java` (`bundleBuild`) |
| image | [`../bundle/Dockerfile`](../bundle/Dockerfile) |
| mini portal | [`../bundle/portal/`](../bundle/portal), served by [`../bundle/Dockerfile.portal`](../bundle/Dockerfile.portal) + [`../bundle/nginx.conf`](../bundle/nginx.conf) |
| runtime templates | `webservice/src/main/resources/life/catalogue/bundle/` |

`WsBundleServer` extends `WsROServer`, so the entire read API — dataset, taxon, tree, name, synonym,
reference, vernacular, verbatim, metrics, parsers — is the same code the public read-only server
runs. On top of that the bundle registers the matching and reconciliation resources and the keyless
rewrite filter.

## Elasticsearch is required

Unlike `WsServer` and `WsROServer`, which fall back to pass-through search when no `es` section is
configured, **a bundle refuses to start without Elasticsearch** (`WsBundleServer.esRequired()`).
There are no `core`/`full` flavors — compose always brings an `elastic` service up.

The index is not shipped. On startup the bundle creates it if it is missing and, when it holds no
documents, indexes the release out of its own Postgres on a daemon thread. Until that finishes the
`bundle-index` health check is unhealthy, which is what the compose `healthcheck` and
`docker compose up --wait` block on. Set `indexOnStart: false` if you pre-warm the elastic volume
yourself.

## Keyless URLs

Because a bundle serves exactly one dataset you do not need to know its key:

```
GET /dataset                 ->  /dataset/{releaseKey}
GET /taxon/{id}/info         ->  /dataset/{releaseKey}/taxon/{id}/info
GET /tree/{id}/children      ->  /dataset/{releaseKey}/tree/{id}/children
GET /nameusage/search?q=     ->  /dataset/{releaseKey}/nameusage/search?q=
GET /reconcile               ->  /dataset/{releaseKey}/reconcile
```

`SingleDatasetRewriteFilter` rewrites the request URI before matching, so every resource class is
reused as is and still receives a `/dataset/{key}/...` path. Keyed URLs keep working, so a client
written against `api.checklistbank.org` needs no changes.

Rewritten first path segments: `archive`, `decision`, `duplicate`, `estimate`, `import`, `issues`,
`logo`, `name`, `nameusage`, `patch`, `reconcile`, `reference`, `sector`, `source`, `synonym`,
`taxon`, `tree`, `verbatim`, `verbatimsource`, `vernacular`. Everything else — `/parser`, `/vocab`,
`/version`, `/nidx`, `/job`, `/match/nameusage`, openapi — is global and untouched.

Two consequences worth knowing:

- `/nameusage` and `/vernacular` also exist as **global** resources in the code. In a bundle the
  rewrite deliberately shadows them; the dataset scoped variants are strictly richer (they add
  `suggest`, `{id}`, `{id}/match`, `pattern`).
- `/match/nameusage` is **not** rewritten. Matching is served by the global
  `FixedNameUsageMatchingResource`, which is already keyless, needs no credentials and streams bulk
  match results straight back to the client instead of parking them in a download server the bundle
  does not have.

`SingleDatasetRewriteFilterTest` derives the rewritten set from the resource `@Path` annotations, so
a new dataset scoped resource fails the build until it is either added to the allowlist or listed as
deliberately not bundled.

## Building the data artifact

`bundleBuild` runs against a **full ChecklistBank database** — it is the source the release is cut
out of. It needs `pg_dump` on the `PATH` and the rights to create a database on that server.

```bash
java -cp webservice/target/webservice-*.jar life.catalogue.WsServer \
  bundleBuild --key 3287 --dir /srv/col-3287-bundle --delete config-prod.yml
```

```bash
java -cp webservice/target/webservice-*.jar life.catalogue.WsServer \
  bundleBuild --key 3287 --dir /srv/col-3287-bundle --delete \
  --image ghcr.io/catalogueoflife/clb-bundle:1.5.2 config-prod.yml
```

What it produces — a directory that is runnable exactly as downloaded:

```
col-3287-bundle/
  release.dump            pg_dump -Fc of a database holding just this release
  nidx/                   names index store
  matcher/{releaseKey}/   usages.bin, canonical.bin, groups.bin, dataset.json
  metrics/                the file based dataset metrics of the release
  bundle.json             release key, title, attempt, source db, build time
  docker-compose.yml      the services, images filled in from --image / --portal-image
  config.yml              the app config, releaseKey filled in
  restore.sh              postgres first boot restore hook
  README.md               what it is and how to use it
```

The last four are generated from the templates in
`webservice/src/main/resources/life/catalogue/bundle/`. They are baked at build time rather than
passed as environment variables because Dropwizard is not set up here to substitute env vars into its
yaml (there is no `SubstitutingSourceProvider`), and because a downloaded artifact that needs editing
before it runs is a bundle in name only. `WsBundleServerConfigTest.shippedTemplateIsValid` parses and
validates the config template, so a broken one fails the build rather than every shipped bundle.

How it gets there — all data tables are hash partitioned on `dataset_key`, so there is no partition
to detach and the release has to be filtered out row by row:

1. create a temporary database on the same server and run the normal `dbschema.sql` + `data.sql`
   schema creation with **one** partition per table;
2. binary `COPY` the release out of the source database and into it — first the global rows whose
   foreign keys everything else hangs off (the `dataset` closure, its citations, archives, sources,
   patches, the mother project's import row, the release's sectors, and the `names_index` rows its
   `name_match` points at), then every partitioned table in `DatasetPartitionMapper.PARTITIONED_TABLES`
   order;
3. rebuild `taxon_metrics` there if the release carried none;
4. build the names index and matcher stores **from that temporary database**, so the nidx ids baked
   into the matcher store are exactly the ids the shipped `names_index` rows carry;
5. copy the mother project's metrics files (a release's file metrics live under the project key and
   its import attempt, see `DatasetImportDao.getReleaseAttempt`);
6. `pg_dump -Fc` the temporary database and drop it again.

The detour through a real database is what makes `release.dump` a plain, self describing dump that
the stock `postgres` image restores with none of our code involved.

The `name_usage` statement triggers that maintain `usage_count` are disabled during the copy — they
build a transition table of everything a `COPY` inserts — and the counter is recomputed afterwards.

## Distributing it

The two halves have opposite properties, so they travel separately:

- **The images are release agnostic** and change with the backend, so they are tagged with the
  backend version and pushed once per release of the code, not once per COL release. There are two,
  `clb-bundle` and `clb-bundle-portal`, and they always carry the same tag.
- **The data artifact is immutable** and multi GB, so it is published as a `tar` next to the other
  downloads of that release, with a `.sha256` beside it. `bundle.json` doubles as its manifest.

Because the compose file and config are inside the artifact, a user needs exactly two things: the
tarball and a working docker. Nothing has to be edited.

## Automation

Three pieces, one per artifact, each triggered by the thing that actually changed.

### The image — GitHub Actions, on a backend release

[`.github/workflows/bundle-image.yml`](../.github/workflows/bundle-image.yml) builds both
`bundle/Dockerfile` and `bundle/Dockerfile.portal` and pushes
`ghcr.io/catalogueoflife/clb-bundle:<version>` and `clb-bundle-portal:<version>` plus `:latest` on
every `v*` tag, using the built in `GITHUB_TOKEN`. A single `tags` job resolves the version so the
two images can never be tagged differently. `workflow_dispatch` rebuilds a tag by hand. Neither
image carries a release key, so one build serves every COL release and the version tracks the
backend.

### The data artifact — a Jenkins job, one argument

[`bundle/Jenkinsfile`](../bundle/Jenkinsfile) takes a single meaningful parameter, `RELEASE_KEY`, and
drives `deploy/bundle.sh` over ssh. The build itself runs on the apps VM rather than on the agent,
because that is where the live config, the database, `pg_dump`, the scratch space and the download
directory already are — nothing multi-GB is ever copied between hosts and no database credentials
have to live in Jenkins. The host, user, deploy path and ssh credential id are job parameters with no
defaults in this repo; set them in the job configuration.

`deploy/bundle.sh` runs `bundleBuild` into scratch space, tars and checksums it, and moves the result
into the download tree with an atomic rename so a half written artifact is never downloadable. Its
`--verify-only` mode re-checks the published files and is what the pipeline's second stage calls, so
a silent failure to publish cannot pass as success.

### What publishing already does by itself

Worth knowing before adding triggers, because most of it needs none. Flipping a release from private
to public emits one `DatasetChanged` event, and the listeners on the broker do the rest **inside the
rw server**:

| | Who | Where |
|---|---|---|
| matcher build | `UsageMatcherFactory.datasetChanged` → `ensurePublishedMatcher` | rw server, for every published dataset above threshold — not release specific |
| concept DOI published, previous release's DOI URL updated | `PublishReleaseListener` | rw server |
| COLDP, DwC-A and TextTree exports + `latest_*` symlinks | `PublishReleaseListener.publishCOL` → `ColReleaseExportJob` | rw server, written into `colDownloadDir/monthly/`. **Only for releases of the COL project** (`sourceKey == Datasets.COL`) — another project's release gets none |
| names archive updated | `PublishReleaseListener` → `NameUsageArchiver` | rw server |
| `publishActions` fired | `PublishReleaseListener`, last | outbound HTTP from the rw server |

So exports and matchers are already automatic. What genuinely needs an outbound trigger is the
**bundle** and, for the extended release, the **portal sitemap** — both too heavy or too far outside
the JVM to run in it.

### The trigger — a release publishAction

The backend already has a post-release hook, so auto-triggering needs no code. `ReleaseAction`
entries in the project's release-config YAML (`Setting.RELEASE_CONFIG` / `XRELEASE_CONFIG`) are fired
as HTTP calls with the release templated into the URL. There are two lists, and the difference
matters:

- `actions` — fired from `ProjectRelease.postMetrics()` when the **release job succeeds**, while the
  release is still private.
- `publishActions` — fired from `PublishReleaseListener` when a release is **published**.

Use `publishActions`: a bundle should only exist for a release the public can actually get.

The configs live in the public
[`CatalogueOfLife/data`](https://github.com/CatalogueOfLife/data) repo — `release-config.yaml` for
the base release and `xrelease/xrelease-config.yaml` for the extended one. Add to both:

```yaml
publishActions:
  # build the release-in-a-box bundle
  - method: POST
    url: "https://builds.gbif.org/view/COL/job/col-bundle/buildWithParameters?token=<job token>&RELEASE_KEY={key}&cause=bundle+for+{key}"
```

The URL is templated by `CitationUtils.fromTemplate` over the release `Dataset`, so any bean property
works — `{key}`, `{alias}`, `{version}`, `{attempt}` — alongside the named `{DATASET_KEY}`,
`{ATTEMPT}`, `{VERSION}`, `{TITLE}`, `{ALIAS}`, `{date}`. A failing action never fails a release or a
publication, but it is no longer silent: `ReleaseAction` logs a warning for anything that is not 2xx
and `callAll` adds a summary line, so a hook whose endpoint moved shows up as a warning instead of
quietly 404ing for months.

Note that a `publishAction` cannot be used to trigger the deploy repo's `publish-col.sh`: that script
is what *causes* publication (`PUT /dataset/{key}/publish`), so firing it from a post-publish hook
would be circular. Only the `actions` list runs early enough for that, and using it would turn
publishing a release from a human decision into an automatic one.

## The mini portal

A bundle built from a **COL release** also ships a small website for browsing that release, served
by an `nginx:alpine` sidecar (the compose `web` service) on <http://localhost/>. The API on 8080 is
untouched, so existing R and OpenRefine clients are unaffected.

| Page | What it is |
|---|---|
| `/` | the COL homepage layout: kingdom tiles and the classification tree |
| `/search` | the full faceted search |
| `/about` | this release's metadata |
| `/sources` | the source datasets, grouped by publisher |
| `/metrics` | headline totals and a rank breakdown |
| `/matching` | upload a CSV/TSV and match it against this release |
| `/taxon/{id}`, `/dataset/{key}` | the detail pages |

It is plain HTML around the published
[`col-browser`](https://github.com/CatalogueOfLife/portal-components) UMD bundle - the same
components the real portal mounts - so there is no node build anywhere in this repo. Look and feel
come from the portal's own `foundation.css` / `style.css` / `custom.css`, copied into
`bundle/portal/css/`.

**It never learns its release key at build time.** One call to the keyless `/api/dataset` - which
`SingleDatasetRewriteFilter` turns into `/dataset/{releaseKey}` - yields the key every component
needs plus the metadata `/about` and the footer render. That is what lets the whole site ship as one
release agnostic image whose tag tracks the backend, exactly like the app image, and it means a
frontend fix reaches an already downloaded artifact by pulling a new image rather than rebuilding
multi GB of data.

`nginx.conf` does two things: it reverse proxies `/api/` to `app:8080` (so the page is same origin
and CORS never enters into it), and it falls `/taxon/…` and `/dataset/…` back to their one page each.
Everything else 404s rather than rendering a detail page with an error in it. Note that rewriting the
`/api` prefix costs the percent encoding - nginx normalises the URI - so an id containing `%2F` would
reach the app as a real slash. COL ids are short opaque strings, and the app's own port 8080 still
reads the raw path, so nothing the portal links to is affected.

### Why COL releases only

The portal is a COL website: COL logos, COL wording, and kingdom tiles keyed to the COL taxon ids
`N`/`P`/`F`/`C`. Pointed at some other project it would misrepresent it. So `BundleBuildCmd` writes
the `web` service into `docker-compose.yml` only when `release.getSourceKey() == Datasets.COL`, and
any other project gets the same API only bundle as before. `--portal true|false` overrides the
detection in either direction; `--portal-image` picks the image, as `--image` does for the app.

Mechanically the compose template carries a `{{PORTAL_SERVICE}}` token that expands either to the
`web:` block (its own template, `docker-compose-portal.yml`) or to nothing.

## Running it

```bash
tar xaf col-3287-bundle.tar.zst && cd col-3287-bundle
docker compose up --wait
```

For a COL release that also brings up the mini portal on <http://localhost/>; the API is on 8080
either way.

`postgres` restores `release.dump` through `/docker-entrypoint-initdb.d` on first boot, `elastic`
comes up empty, and the app fills it. First boot on a full COL release therefore takes a while;
subsequent starts are immediate because both volumes persist.

The bundle data volume is mounted **read-write**: the names index grows as new names are matched and
the matcher store keeps its taxonomic group cache in `groups.bin`.

## What a bundle does not do

No import, sync, release, export-job or admin write endpoints. No GBIF registry authentication (the
config ships an empty `MapAuthenticationFactory`), no mail, no DOI registration.

**Bulk matching is streaming only.** The mini portal's `/matching` page posts the raw file to the
keyless `POST /match/nameusage`, which needs no credentials and streams the annotated result back on
the same request. There is no job to poll and no result to re-download later: the async
`POST /dataset/{key}/match/nameusage/job` requires `@Auth`, and nobody can authenticate against an
empty user map. Two details bite anyone calling it directly - the request is **not** multipart (raw
bytes with a `text/*` content type; `FormData` gets a 415), and the response is a **ZIP** even though
it is labelled `text/plain`.

The mini portal is a COL-release-only extra and is absent from every other bundle, see
[Why COL releases only](#why-col-releases-only).
