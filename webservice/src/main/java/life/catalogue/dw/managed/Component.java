package life.catalogue.dw.managed;

/**
 * Start/stoppable components in the order they should be started; stopAll reverses it.
 *
 * A component earns its place here by being one of three things, and nothing else belongs:
 * <ul>
 *   <li><b>an exclusive resource</b> - {@link #NamesIndex}, whose chronicle map is opened read-write and
 *       therefore has to be handed over between two JVMs during a deploy or a swap;</li>
 *   <li><b>the work engine</b> - {@link #JobExecutor}. Stopping it rejects submissions, interrupts what runs
 *       and discards the queue. To quiesce for maintenance without losing the queue, pause it instead
 *       (POST /admin/jobs/pause);</li>
 *   <li><b>an autonomous writer</b> - everything that acts without an HTTP request behind it and so has to be
 *       silenced on the old app before the new one takes over: the cron executor, the DOI listener, the two
 *       polling schedulers, the GBIF registry sync and the feedback service.</li>
 * </ul>
 *
 * Starting all of them is therefore the read/write switch: with them stopped this app does nothing of its own
 * accord. Requests it is still served can of course still write - the CRUD API writes on the request thread
 * and never goes near a job - which is a separate concern this mechanism does not address.
 *
 * What does <em>not</em> belong here is a submit gate in front of the shared job executor. DatasetImporter and
 * SectorSynchronizer were exactly that: their stop() stopped no work, as their own comments admitted, because
 * the running and queued jobs live in the executor. UsageMatcher was not even a gate - nothing ever asked
 * whether it had started.
 *
 * Nothing here cascades: stopping a component stops that component only. The declaration order above and the
 * point of use assertOnline() calls - all of them on the names index - are the whole of the dependency
 * handling, so an operator taking the names index down has to pause the job executor first and wait for it
 * to quiesce. See docs/2026-09-01-job-component-consolidation.md and deploy's nidx-swap.sh.
 */
public enum Component {
  NamesIndex,
  JobExecutor,
  CronExecutor,
  DoiUpdater,
  ImportScheduler,
  SyncScheduler,
  GBIFRegistrySync,
  Feedback
}
