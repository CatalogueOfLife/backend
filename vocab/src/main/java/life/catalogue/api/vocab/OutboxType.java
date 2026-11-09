package life.catalogue.api.vocab;

/**
 * The kinds of durable command the outbox holds. Each is run by exactly one handler on the app whose
 * Outbox component is started, see life.catalogue.outbox.OutboxRunner.
 */
public enum OutboxType {

  /**
   * Create, update, publish or delete a DOI at DataCite.
   * One row per DOI, the requested actions are flags in its params.
   */
  DOI,

  /**
   * Delete a sector of a project, keyed by "projectKey:sectorId".
   */
  SECTOR_DELETE,

  /**
   * A release was made public: archive its names, build the COL exports and fire the publish actions.
   * Keyed by the release key.
   */
  RELEASE_PUBLISHED;
}
