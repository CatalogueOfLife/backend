package life.catalogue.api.event;

/**
 * Message to inform subscribers about a change of the dataset logo image.
 */
public class DatasetLogoChanged implements Event {
  public int datasetKey;

  public DatasetLogoChanged() {
  }

  public DatasetLogoChanged(int datasetKey) {
    this.datasetKey = datasetKey;
  }

}
