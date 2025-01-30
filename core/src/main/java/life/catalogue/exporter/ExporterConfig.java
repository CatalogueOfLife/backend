package life.catalogue.exporter;

import life.catalogue.concurrent.JobConfig;
import life.catalogue.config.NormalizerConfig;
import life.catalogue.img.ImgConfig;

import java.net.URI;

public interface ExporterConfig {

  URI getApiUri();
  JobConfig getJob();
  ImgConfig getImgConfig();
  NormalizerConfig getNormalizerConfig();
}
