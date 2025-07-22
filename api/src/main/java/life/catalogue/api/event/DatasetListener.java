package life.catalogue.api.event;

public interface DatasetListener extends Listener {

  void datasetChanged(DatasetChanged d);

  default void datasetDataChanged(DatasetDataChanged event) {
    // nothing, override if needed
  }

  default void datasetLogoChanged(DatasetLogoChanged event) {
    // nothing, override if needed
  }

}
