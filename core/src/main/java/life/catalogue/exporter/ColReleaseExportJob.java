package life.catalogue.exporter;

import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetExport;
import life.catalogue.api.model.ExportRequest;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.common.io.PathUtils;
import life.catalogue.concurrent.DatasetBlockingJob;
import life.catalogue.api.vocab.JobPriority;
import life.catalogue.config.ReleaseConfig;
import life.catalogue.img.ImageService;

import java.io.File;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wraps the dataset exports of one COL release and copies the resulting files to the COL download server,
 * updating latest symlinks.
 *
 * All formats of one release run in a single job because they all block on that release's dataset lock.
 * As separate jobs only one of them ever won the lock; the others were rejected, resubmitted and then slept
 * on a worker thread with an escalating backoff of up to five minutes a try, which both wasted most of the
 * time between two exports and held down two of the executor's threads while doing nothing.
 */
public class ColReleaseExportJob extends DatasetBlockingJob {
  private static final Logger LOG = LoggerFactory.getLogger(ColReleaseExportJob.class);
  private final ReleaseConfig rCfg;
  /** one export per requested format, in the order they are to be run */
  private final Map<DataFormat, DatasetExportJob> exportJobs = new LinkedHashMap<>();
  private final boolean latest;

  public ColReleaseExportJob(int datasetKey, int userKey, boolean latest, Collection<DataFormat> formats,
                             ReleaseConfig rcfg, ExporterConfig ecfg, SqlSessionFactory factory) {
    super(datasetKey, userKey, JobPriority.HIGH);
    this.latest = latest;
    this.rCfg = rcfg;
    this.dataset = loadDataset(factory, datasetKey);

    if (!dataset.getOrigin().isRelease() || dataset.getSourceKey() != Datasets.COL) {
      throw new IllegalArgumentException("Only COL releases are supported, not dataset " + datasetKey);
    }

    for (DataFormat format : formats) {
      ExportRequest req = new ExportRequest(datasetKey, format);
      req.setExcel(false);
      req.setExtended(format != DataFormat.TEXT_TREE);

      exportJobs.put(format, switch (format) {
        case COLDP -> new ColdpExtendedExport(req, userKey, factory, ecfg, ImageService.passThru());
        case DWCA -> new DwcaExtendedExport(req, userKey, factory, ecfg, ImageService.passThru());
        case TEXT_TREE -> new TextTreeExport(req, userKey, factory, ecfg, ImageService.passThru());
        default -> throw new IllegalArgumentException("Export format " + format + " is not supported yet");
      });
    }
  }

  @Override
  protected void runWithLock() throws Exception {
    List<DataFormat> failed = new ArrayList<>();
    for (var entry : exportJobs.entrySet()) {
      final DataFormat format = entry.getKey();
      final DatasetExportJob exportJob = entry.getValue();
      // one bad format must not cost the release its other downloads, so each is judged on its own
      checkIfCancelled();
      LOG.info("Starting COL export job for dataset {} in format {}", datasetKey, format);
      exportJob.skipLock();
      exportJob.run();
      // the inner export swallows its own InterruptedException, but the thread interrupt
      // flag stays set - re-check so we don't copy an incomplete archive after a cancel
      checkIfCancelled();

      if (exportJob.isFinished()) {
        LOG.info("Copy {} export file from {} to COL download server", format, exportJob.archive);
        copyToCol(format, exportJob);
        LOG.info("Finished COL {} export job for dataset {}", format, datasetKey);
      } else {
        failed.add(format);
        LOG.error("COL {} export for dataset {} ended as {}. Not copied to the download server",
          format, datasetKey, exportJob.getStatus());
      }
    }
    if (!failed.isEmpty()) {
      throw new IllegalStateException("COL exports of dataset " + datasetKey + " failed for " + failed);
    }
  }

  public Collection<DataFormat> getFormats() {
    return exportJobs.keySet();
  }

  public DatasetExport getExport(DataFormat format) {
    var job = exportJobs.get(format);
    return job == null ? null : job.getExport();
  }

  private void copyToCol(DataFormat format, DatasetExportJob exportJob) {
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
