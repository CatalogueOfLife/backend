package life.catalogue;

import life.catalogue.concurrent.JobConfig;
import life.catalogue.config.GbifConfig;
import life.catalogue.config.ImporterConfig;
import life.catalogue.config.NormalizerConfig;
import life.catalogue.config.ReleaseConfig;
import life.catalogue.db.PgConfig;
import life.catalogue.doi.service.DoiConfig;
import life.catalogue.exporter.ExporterConfig;
import life.catalogue.img.ImgConfig;

import java.net.URI;

import org.apache.commons.io.FileUtils;

import com.google.common.io.Files;

public class TestConfigs implements ExporterConfig {
  public URI apiURI = ApiUtils.API;
  public URI clbURI = URI.create("http://localhost/clb");
  public URI portalURI = URI.create("http://localhost/portal");

  public ImporterConfig importer = new ImporterConfig();
  public NormalizerConfig normalizer = new NormalizerConfig();
  public ImgConfig img = new ImgConfig();
  public ReleaseConfig release = new ReleaseConfig();
  public JobConfig job = new JobConfig();
  public GbifConfig gbif = new GbifConfig();
  public DoiConfig doi;
  public PgConfig db;

  public static TestConfigs build() {
    var cfg = new TestConfigs();
    cfg.release.restart = null;

    cfg.gbif.fullSyncFrequency = 0;

    cfg.importer.continuous.polling = 0;
    cfg.importer.threads = 2;
    // wait for half a minute before completing an import to run assertions
    cfg.importer.wait = 30;

    cfg.normalizer.archiveDir = Files.createTempDir();
    cfg.normalizer.scratchDir = Files.createTempDir();
    cfg.img.repo = cfg.normalizer.scratchDir.toPath();
    cfg.job.downloadDir = Files.createTempDir();

    return cfg;
  }

  public void removeCfgDirs() {
    FileUtils.deleteQuietly(job.downloadDir);
    FileUtils.deleteQuietly(normalizer.scratchDir);
    FileUtils.deleteQuietly(normalizer.archiveDir);
  }

  @Override
  public URI getApiUri() {
    return apiURI;
  }

  @Override
  public JobConfig getJob() {
    return job;
  }

  @Override
  public ImgConfig getImgConfig() {
    return img;
  }

  @Override
  public NormalizerConfig getNormalizerConfig() {
    return normalizer;
  }
}
