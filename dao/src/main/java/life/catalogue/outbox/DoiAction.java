package life.catalogue.outbox;

/**
 * What should happen to a DOI at DataCite. Several actions requested for the same DOI are flags of a single outbox
 * command, run in the order declared here: a delete makes all others pointless, a create already sends the
 * metadata, and the metadata is updated before the DOI is published, so it becomes findable with current metadata.
 */
public enum DoiAction {
  DELETE,
  CREATE,
  UPDATE,
  PUBLISH
}
