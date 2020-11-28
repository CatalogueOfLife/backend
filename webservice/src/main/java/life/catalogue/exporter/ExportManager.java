package life.catalogue.exporter;

import life.catalogue.WsServerConfig;
import life.catalogue.common.concurrent.BackgroundJob;
import life.catalogue.common.concurrent.JobExecutor;
import org.apache.ibatis.session.SqlSessionFactory;

import java.io.File;
import java.net.URI;
import java.util.UUID;

public class ExportManager {
  private final WsServerConfig cfg;
  private final SqlSessionFactory factory;
  private final JobExecutor executor;

  public ExportManager(WsServerConfig cfg, SqlSessionFactory factory, JobExecutor executor) {
    this.cfg = cfg;
    this.factory = factory;
    this.executor = executor;
  }

  public File archiveFiLe(UUID key) {
    return ArchiveExporter.archive(cfg.exportDir, key);
  }

  public URI archiveURI(UUID key) {
    String path = archiveFiLe(key).getAbsolutePath().substring(cfg.exportDir.getAbsolutePath().length());
    return cfg.downloads.resolve("/exports" + path);
  }

  public UUID sumit(ExportRequest req){
    BackgroundJob job;
    switch (req.getFormat()) {
      case COLDP:
        job = new DwcaExporter(req, factory, cfg.exportDir);
        break;
      case ACEF:
        job = new AcefExporterJob(req, cfg, factory);
        break;

      default:
        throw new IllegalArgumentException("Export format "+req.getFormat() + " is not supported yet");
    }
    executor.submit(job);
    return job.getKey();
  }
}
