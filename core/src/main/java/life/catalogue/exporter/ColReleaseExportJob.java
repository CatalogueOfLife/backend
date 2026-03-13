package life.catalogue.exporter;

import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.ExportRequest;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.common.io.PathUtils;
import life.catalogue.concurrent.DatasetBlockingJob;
import life.catalogue.concurrent.JobPriority;
import life.catalogue.config.ReleaseConfig;
import life.catalogue.img.ImageService;

import java.io.File;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wraps a dataset export job and copies the resulting files to the COL download server, updating latest symlinks.
 */
public class ColReleaseExportJob extends DatasetBlockingJob {
  private static final Logger LOG = LoggerFactory.getLogger(ColReleaseExportJob.class);
  private final DataFormat format;
  private final ReleaseConfig rCfg;
  private final DatasetExportJob exportJob;
  private final boolean latest;

  public ColReleaseExportJob(int datasetKey, int userKey, boolean latest, DataFormat format, ReleaseConfig rcfg, ExporterConfig ecfg, SqlSessionFactory factory) {
    super(datasetKey, userKey, JobPriority.HIGH);
    this.format = format;
    this.latest = latest;
    this.rCfg = rcfg;

    if (!dataset.getOrigin().isRelease() || dataset.getSourceKey() != Datasets.COL) {
      throw new IllegalArgumentException("Only COL releases are supported, not dataset " + datasetKey);
    }

    ExportRequest req = new ExportRequest();
    req.setDatasetKey(datasetKey);
    req.setFormat(format);
    req.setExcel(false);
    req.setExtended(format != DataFormat.TEXT_TREE);

    exportJob = switch (format) {
      case COLDP -> new ColdpExtendedExport(req, userKey, factory, ecfg, ImageService.passThru());
      case DWCA -> new DwcaExtendedExport(req, userKey, factory, ecfg, ImageService.passThru());
      case TEXT_TREE -> new TextTreeExport(req, userKey, factory, ecfg, ImageService.passThru());
      default -> throw new IllegalArgumentException("Export format " + format + " is not supported yet");
    };
  }

  @Override
  protected void runWithLock() throws Exception {
    LOG.info("Starting COL export job for dataset {} in format {}", datasetKey, format);
    exportJob.skipLock();
    exportJob.run();

    LOG.info("Copy {} export file from {} to COL download server", format, exportJob.archive);
    copyToCol();

    LOG.info("Finished COL {} export job for dataset {}", format, datasetKey);
  }

  private void copyToCol() {
    copyToCol(dataset, rCfg.colDownloadDir, format, exportJob.getKey(), exportJob.archive, latest);
  }

  public static void copyToCol(Dataset dataset, File colDownloadDir, DataFormat format, UUID exportJobKey, File source, boolean latest) {
    if (colDownloadDir != null) {
      if (dataset.getIssued() == null) {
        LOG.error("COL release {} is missing a release date. Unable to copy {} export", dataset.getKey(), format);
        return;
      }
      if (source.exists()) {
        final File target = colDownloadFile(colDownloadDir, dataset, format);
        try {
          LOG.info("Copy COL {} export {} to {}", format, exportJobKey, target);
          FileUtils.copyFile(source, target);
          if (latest) {
            File symlink = colLatestFile(colDownloadDir, dataset.getOrigin(), format);
            LOG.info("Symlink COL {} export {} at {} to {}", format, exportJobKey, target, symlink);
            PathUtils.symlink(symlink, target);
          }
        } catch (IOException e) {
          LOG.error("Failed to copy COL {} export {} to {}", format, source, target, e);
        }
      } else {
        LOG.warn("COL {} export {} does not exist at expected location {}", format, exportJobKey, source);
      }
    } else {
      LOG.warn("No colDownloadDir configured!");
    }
  }

  public static String prefix(DatasetOrigin origin) {
    return origin == DatasetOrigin.XRELEASE ? "xr_" : "";
  }

  private static File colLatestFile(File colDownloadDir, DatasetOrigin origin, DataFormat format) {
    return new File(colDownloadDir, prefix(origin) + "latest_" + format.getFilename() + ".zip");
  }

  private static File colDownloadFile(File colDownloadDir, Dataset dataset, DataFormat format) {
    String iso = DateTimeFormatter.ISO_DATE.format(dataset.getIssued().getDate());
    return new File(colDownloadDir, "monthly/" + iso + "_" + prefix(dataset.getOrigin()) + format.getFilename() + ".zip");
  }
}
