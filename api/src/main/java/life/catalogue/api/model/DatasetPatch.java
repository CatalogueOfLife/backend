package life.catalogue.api.model;

import java.util.Map;
import java.util.Objects;

/**
 * Editorial decision patching the metadata of a source dataset.
 * The integer id value of the underlying DSID<Integer> refers to the source dataset key.
 */
public class DatasetPatch extends DatasetScopedEntity<Integer> {

  /**
   * Patch of JSON dataset property names as keys and their patched value.
   * An existing key with a value of NULL indicates the value should be removed.
   */
  Map<String, Object> patch;

  public static DatasetPatch create(int projectKey, Dataset d) {
    DatasetPatch p = new DatasetPatch();
    p.setId(d.getKey());
    p.setDatasetKey(projectKey);
    return p;
  }

  public Map<String, Object> getPatch() {
    return patch;
  }

  public void setPatch(Map<String, Object> patch) {
    this.patch = patch;
  }

  /**
   * Patches the given dataset with the patch information.
   * The method will modify the given object and not create a new instance.
   * @return the given and patched object
   */
  public Dataset patch(Dataset d) {
    //TODO:
    return d;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    DatasetPatch that = (DatasetPatch) o;
    return Objects.equals(patch, that.patch);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), patch);
  }
}
