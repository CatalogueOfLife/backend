package life.catalogue.api.vocab;

/**
 * Vocabulary for the source of truth for the data of a dataset.
 */
public enum DatasetOrigin {
  
  /**
   * A dataset which is synchronised from an external archive pulled from a URL at a regular interval.
   */
  EXTERNAL,

  /**
   * A project dataset which is managed directly inside the Clearinghouse through the taxonomic editor.
   */
  PROJECT,

  /**
   * A previously managed dataset that has been released into an immutable release copy.
   */
  RELEASE,

  /**
   * An extended release pointing to a base release via the sourceKey property.
   */
  XRELEASE;

  /**
   * @return true if the origin is a project with decisions and sectors, i.e. the origin is either managed or released.
   */
  public boolean isManagedOrRelease() {
    return this != EXTERNAL;
  }

  /**
   * @return true if origin is a regular or extended release
   */
  public boolean isRelease() {
    return this == RELEASE || this == XRELEASE;
  }
}
