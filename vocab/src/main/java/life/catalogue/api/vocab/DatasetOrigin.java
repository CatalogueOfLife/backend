package life.catalogue.api.vocab;

/**
 * Vocabulary for the source of truth for the data of a dataset.
 */
public enum DatasetOrigin implements Described {

  EXTERNAL("A dataset which is synchronised from an external archive pulled from a URL at a regular interval."),

  PROJECT("A project dataset which is managed directly inside ChecklistBank through the taxonomic editor."),

  RELEASE("A previously managed dataset that has been released into an immutable release copy."),

  XRELEASE("An extended release pointing to a base release via the sourceKey property.");

  private final String description;

  DatasetOrigin(String description) {
    this.description = description;
  }

  @Override
  public String getDescription() {
    return description;
  }

  /**
   * @return true if the origin is a project with decisions and sectors, i.e. the origin is either managed or released.
   */
  public boolean isProjectOrRelease() {
    return this != EXTERNAL;
  }

  /**
   * @return true if origin is a regular or extended release
   */
  public boolean isRelease() {
    return this == RELEASE || this == XRELEASE;
  }
}
