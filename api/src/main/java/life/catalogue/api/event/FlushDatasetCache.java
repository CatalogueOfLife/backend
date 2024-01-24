package life.catalogue.api.event;

/**
 * Instructs any cache e.g. varnish to flush data for the given dataset
 */
public class FlushDatasetCache {
  public final int datasetKey;

  public FlushDatasetCache(int datasetKey) {
    this.datasetKey = datasetKey;
  }
}
