package life.catalogue.exporter;

import com.google.common.base.Preconditions;
import life.catalogue.WsServerConfig;
import life.catalogue.api.model.DSID;
import life.catalogue.common.concurrent.BackgroundJob;
import life.catalogue.common.concurrent.JobExecutor;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.mapper.TaxonMapper;
import org.apache.ibatis.session.SqlSession;
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
    return cfg.downloadURI.resolve("/exports" + path);
  }

  public UUID sumit(ExportRequest req){
    validate(req);
    BackgroundJob job;
    switch (req.getFormat()) {
      case DWCA:
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

  /**
   * Makes sure taxonID exists if given
   */
  void validate(ExportRequest req) throws IllegalArgumentException {
    if (req.getTaxonID() != null) {
      try (SqlSession session = factory.openSession()) {
        if (session.getMapper(NameUsageMapper.class).getSimple(DSID.of(req.getDatasetKey(), req.getTaxonID())) == null) {
          throw new IllegalArgumentException("Root taxonID " + req.getTaxonID() + " does not exist in dataset " + req.getDatasetKey());
        }
      }
    }
  }
}
