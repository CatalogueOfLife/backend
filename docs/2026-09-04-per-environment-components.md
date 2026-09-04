# Let each environment decide which components it runs

Date: 2026-09-04
Status: Shipped in backend, deploy and checklistbank. Closes outstanding item 2 of
[2026-09-01-job-component-consolidation.md](2026-09-01-job-component-consolidation.md).

## Problem

`/admin/component/start-all` started all eight components everywhere. Nothing in the JVM knows which
environment it is - `ENV` exists only in deploy's shell - so the only per environment differentiation
was a script, `deploy/start-dev-components.sh`, holding the intended dev subset.

That script was called from exactly one place, `nidx-clear.sh`, and called it env blind. So:

- **dev and test did start `Feedback`, `DoiUpdater`, `CronExecutor` and `ImportScheduler`**, because
  `redeploy.sh`, `restart.sh` and `nidx-swap.sh` all call `start-all-components.sh` regardless of env.
  Dev was one github token away from opening real issues on a public repo.
- **`nidx-clear.sh` was env blind the other way round**: it restarted with the dev subset, so running it
  on prod would have left prod running four of its eight components.
- **The health banner was already wrong on dev.** `FeedbackService.passThru()` was registered like a real
  component and its `hasStarted()` returns a hardcoded `false`, so `GET /admin/component` reported
  `Feedback: false` forever and the ChecklistBank UI went red on a perfectly healthy dev server.

## Goals

1. What an environment runs is a property of that environment's config, not of which script an operator
   happened to run.
2. A component that has no business existing in an environment cannot be switched on there by accident.
3. The health banner distinguishes "deliberately off here" from "should be running and is not".

## Non-goals

- **Teaching the app its environment name.** A `env: dev` key plus per environment policy in java would put
  the policy in the mechanism, and the policy is exactly what differs. The config states the outcome instead.
- **A dependency graph between components.** Still nothing cascades; see the 2026-09-01 record.
- **Per environment behaviour beyond start-up.** The DataCite and GBIF endpoints, the mail subject prefix and
  the ES index name already differ per environment through their own config, and stay that way.

## Decision

**One config key, a map of deviations from the default**, so prod carries no key at all:

```yaml
components:
  Feedback: DISABLED
  DoiUpdater: DISABLED
  CronExecutor: MANUAL
  ImportScheduler: MANUAL
```

A boolean would not have done, because two different things were wanted of dev:

| Mode | Managed | In `/admin/component` | Started by `start-all` | Startable by hand |
|---|---|---|---|---|
| `AUTO` (default) | yes | yes | yes | yes |
| `MANUAL` | yes | yes | no | yes |
| `DISABLED` | no | no | no | no |

`Feedback` opens public github issues and `DoiUpdater` registers DataCite DOIs; neither belongs on dev
under any circumstance, not even switched on by hand from the admin UI, so they are `DISABLED` - not
registered at all, and therefore indistinguishable from a component the server never wired. `CronExecutor`
and `ImportScheduler` are the opposite case: harmless to have around and occasionally worth exercising,
they just have no business polling by themselves on a machine nobody is watching, so they are `MANUAL`.

`WsServer` additionally selects the *existing* `FeedbackService.passThru()` / `DoiService.passThru()` when
those two are disabled. Both instances are wired into the REST resources and the event broker, which the
component lifecycle does not gate, so "unstartable" is not the same as inert. Reusing the pass-throughs
means a disabled service degrades exactly the way an unconfigured one already did, with no new concept.

**`stop-all` still stops everything managed, `MANUAL` included.** It is the blue-green read/write switch and
must not leave a hand started scheduler alive on the app being replaced. `restart-all` is `stopAll` +
`startAll` and therefore leaves the manual ones stopped, which is the intended reading of "manual".

**`GET /admin/component` gained structure** so the UI can tell the two apart:

```json
{"idle": true, "quiesced": true,
 "components": {"NamesIndex": {"running": true, "autostart": true},
                "ImportScheduler": {"running": false, "autostart": false}}}
```

`idle` moved out of the component map - it was never a component, and both UI call sites already had to
filter it out - and `quiesced` was added, which `Admin/index.jsx` had been reading and always getting
`undefined` for. The banner becomes `every(c => !c.autostart || c.running)`.

### Rejected: per component config gates only

No new key; instead move each component's existing gate out of `start()` into the wiring, so an ungated
component is simply not registered, and invent a flag for `CronExecutor`. Six of the eight already have
some gate (`importer.continuous.polling`, `gbif.*SyncFrequency`, a null `github`/`doi` block), so dev would
express itself entirely through config it has to set correctly anyway, with nothing to contradict.

Against it: the gates end up scattered over eight sites with no single place to read what an environment
runs, `CronExecutor` needs a flag invented for it alone, and a fully configured component could not be
switched off - dev points at the DataCite *test* API, so `doi` is legitimately configured there. The
existing gates were kept as a safety net rather than promoted to the mechanism.

### Rejected: a COMPONENTS var in the deploy scripts

`configs/$ENV/config.sh` gets `COMPONENTS="NamesIndex JobExecutor ..."` and the start scripts pass it
through. Cheapest by far, and it is what `start-dev-components.sh` already was. But the knowledge stays
outside the app: the admin UI keeps warning on dev, an operator can still flip `Feedback` on by hand, and
every script that starts components has to remember to consult it - which is precisely the bug
`nidx-clear.sh` had.

## Outcome

Deviations from the plan worth recording:

- **`DISABLED`, not `OFF`.** The design said `OFF`, which cannot work: yaml 1.1 resolves a bare `OFF` - like
  `ON`, `YES` and `NO` - to a boolean, so `Feedback: OFF` reaches jackson as `false` and fails to bind to the
  enum. Caught by the binding test, which exists for this class of failure and is why it was written.
- **The plan's shipping-order note was wrong about unknown properties.** Dropwizard's yaml factory here
  *silently ignores* what it cannot bind (see the comment on `WsServerConfigTest.environmentConfigsMatchConfigClasses`),
  so a `components:` block landing before the backend that understands it would not fail the deploy - it
  would quietly start the very component the environment wanted gone. A bad *value* does still throw, which
  is what makes the enum naming matter.
- **`new EnumMap<>(otherMap)` throws on an empty non-enum map.** A literal `components: {}` in yaml yields an
  empty `LinkedHashMap`, so `ManagedService` builds its `EnumMap` and `putAll`s instead.
- **Outstanding item 1 of the 2026-09-01 record was a non-issue.** Both ChecklistBank call sites already
  rendered the component map dynamically; no component name was ever hardcoded there.

### Shipping

Backend, deploy and checklistbank went out together. The UI had to, since an old `Admin/index.jsx` against
the new payload renders switches labelled `idle`, `quiesced` and `components`. `deploy/start-dev-components.sh`
is deleted and `nidx-clear.sh` now restarts with `start-all-components.sh` like every other script, which is
the env-blind bug fixed.
