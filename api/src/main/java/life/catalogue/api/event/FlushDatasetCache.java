package life.catalogue.api.event;

/**
 * Instructs any cache e.g. varnish to flush data for the given dataset
 */
public class FlushDatasetCache {
  public final int datasetKey;
  public final boolean logoOnly;

  public static FlushDatasetCache all() {
    return new FlushDatasetCache(-1);
  }

  public FlushDatasetCache(int datasetKey) {
    this(datasetKey, false);
  }
  public FlushDatasetCache(int datasetKey, boolean logoOnly) {
    this.datasetKey = datasetKey;
    this.logoOnly = logoOnly;
  }
}
