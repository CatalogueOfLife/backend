package life.catalogue.api.model;

import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.DatasetType;
import life.catalogue.api.vocab.License;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Counts of the ChecklistBank datasets of a GBIF publisher, by origin, type and license.
 * Deleted and temporary datasets are not counted, nor private ones the user cannot read.
 * Datasets without a license are missing from the license counts.
 */
public class PublisherMetrics {
  private UUID key;
  private int datasets;
  private int privat;
  private long usages;
  private final Map<DatasetOrigin, Integer> origin = new EnumMap<>(DatasetOrigin.class);
  private final Map<DatasetType, Integer> type = new EnumMap<>(DatasetType.class);
  private final Map<License, Integer> license = new EnumMap<>(License.class);

  public PublisherMetrics() {
  }

  public PublisherMetrics(UUID key) {
    this.key = key;
  }

  /**
   * Adds a group of datasets sharing origin, type, license and the private flag.
   */
  public void add(DatasetOrigin o, DatasetType t, License l, boolean priv, int count, long usages) {
    datasets += count;
    if (priv) {
      privat += count;
    }
    this.usages += usages;
    if (o != null) origin.merge(o, count, Integer::sum);
    if (t != null) type.merge(t, count, Integer::sum);
    if (l != null) license.merge(l, count, Integer::sum);
  }

  public UUID getKey() {
    return key;
  }

  public void setKey(UUID key) {
    this.key = key;
  }

  /**
   * @return number of all datasets counted
   */
  public int getDatasets() {
    return datasets;
  }

  /**
   * @return number of private datasets among them
   */
  public int getPrivate() {
    return privat;
  }

  /**
   * @return sum of the name usages of all datasets counted
   */
  public long getUsages() {
    return usages;
  }

  public Map<DatasetOrigin, Integer> getOrigin() {
    return origin;
  }

  public Map<DatasetType, Integer> getType() {
    return type;
  }

  public Map<License, Integer> getLicense() {
    return license;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof PublisherMetrics)) return false;
    PublisherMetrics that = (PublisherMetrics) o;
    return datasets == that.datasets && privat == that.privat && usages == that.usages &&
      Objects.equals(key, that.key) && origin.equals(that.origin) && type.equals(that.type) && license.equals(that.license);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, datasets, privat, usages, origin, type, license);
  }
}
