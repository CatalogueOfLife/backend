package life.catalogue.exporter;

import life.catalogue.common.concurrent.BackgroundJob;

import java.util.function.Consumer;

public class EmailNotificationHandler implements Consumer<BackgroundJob> {

  @Override
  public void accept(BackgroundJob datasetExporter) {
    //TODO:
  }
}
