package life.catalogue.exporter;

import com.google.common.base.Preconditions;
import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.Dataset;
import life.catalogue.common.concurrent.DatasetBlockingJob;
import life.catalogue.common.concurrent.JobPriority;
import life.catalogue.common.io.CompressionUtil;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.DatasetPartitionMapper;
import org.apache.commons.io.FileUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

abstract class DatasetExporter extends DatasetBlockingJob {
  private static final Logger LOG = LoggerFactory.getLogger(DatasetExporter.class);

  protected final SqlSessionFactory factory;
  protected final ExportRequest req;
  protected final Dataset dataset;
  protected File archive;
  protected File tmpDir;

  DatasetExporter(ExportRequest req, SqlSessionFactory factory, File exportDir) {
    super(req.getDatasetKey(), req.getUserKey(), JobPriority.LOW);
    this.req = Preconditions.checkNotNull(req);
    this.factory = factory;
    this.archive = archive(exportDir, getKey());
    this.tmpDir = new File(exportDir, getKey().toString());
    try (SqlSession session = factory.openSession(false)) {
      dataset = session.getMapper(DatasetMapper.class).get(datasetKey);
      if (dataset == null || dataset.getDeleted() != null) {
        throw new NotFoundException("Dataset "+datasetKey+" does not exist");
      }
      if (!session.getMapper(DatasetPartitionMapper.class).exists(datasetKey)) {
        throw new IllegalArgumentException("Dataset "+datasetKey+" does not have any data");
      }
    }
    LOG.info("Created {} job {} by user {} for dataset {} to {}", getClass().getSimpleName(), getUserKey(), getKey(), datasetKey, archive);
  }

  static File archive(File exportDir, UUID key) {
    return new File(exportDir, key.toString().substring(0,2) + "/" + key.toString() + ".zip");
  }

  public ExportRequest getReq() {
    return req;
  }

  public File getArchive() {
    return archive;
  }

  @Override
  public final void runWithLock() throws Exception {
    FileUtils.forceMkdir(tmpDir);
    try {
      export();
      bundle();
      LOG.info("Export {} of dataset {} completed", getKey(), datasetKey);
    } finally {
      LOG.info("Remove temporary export directory {}", tmpDir.getAbsolutePath());
      try {
        FileUtils.deleteDirectory(tmpDir);
      } catch (IOException e) {
        LOG.info("Failed to delete temporary export directory {}", tmpDir.getAbsolutePath(), e);
      }
    }
  }

  protected void bundle() throws IOException {
    LOG.info("Bundling archive at {}", archive.getAbsolutePath());
    FileUtils.forceMkdir(archive.getParentFile());
    CompressionUtil.zipDir(tmpDir, archive, true);
  }

  protected abstract void export() throws Exception;

}
