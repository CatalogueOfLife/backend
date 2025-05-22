package life.catalogue.api.event;

/**
 * Message to inform subscribers about a change of the dataset logo image.
 */
public class DatasetLogoChanged {
  public final int datasetKey;

  public DatasetLogoChanged(int datasetKey) {
    this.datasetKey = datasetKey;
  }

}
