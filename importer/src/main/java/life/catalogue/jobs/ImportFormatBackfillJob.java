package life.catalogue.jobs;

import life.catalogue.api.jackson.PermissiveEnumSerde;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.JobPriority;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.config.NormalizerConfig;
import life.catalogue.db.mapper.DatasetImportMapper;
import life.catalogue.importer.DataFormatDetector;

import java.io.File;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Records the data format of past imports that have none.
 * Imports of datasets whose settings configure a data format skipped the format detection and never recorded the
 * format, which GBIF registration and the format filter of the import search read. The format of each such import is
 * detected from its stored source archive without extracting it, see DataFormatDetector.detectArchive.
 * The earliest imports have no archive stored anymore: they take the format of another import of the same dataset
 * with the same source MD5 if there is one, and keep none otherwise.
 *
 * A dry run changes nothing and only logs what it would set. Either way every detected format that differs from the
 * one currently configured for the dataset is logged.
 */
public class ImportFormatBackfillJob extends BackgroundJob {
  private static final Logger LOG = LoggerFactory.getLogger(ImportFormatBackfillJob.class);
  private static final int STEP_BATCH = 100;

  private final SqlSessionFactory factory;
  private final NormalizerConfig nCfg;
  private final boolean dryRun;
  private final Map<DataFormat, Integer> detected = new EnumMap<>(DataFormat.class);
  private int total;
  private int processed;
  private int noArchive;
  private int undetected;
  private int conflicts;
  private int sameSource;

  public ImportFormatBackfillJob(int userKey, SqlSessionFactory factory, NormalizerConfig nCfg, boolean dryRun) {
    super(JobPriority.LOW, userKey);
    this.factory = factory;
    this.nCfg = nCfg;
    this.dryRun = dryRun;
  }

  @Override
  public Map<String, Object> getParams() {
    return Map.of("dryRun", dryRun);
  }

  @Override
  public void execute() throws Exception {
    final List<DatasetImportMapper.MissingFormat> missing;
    try (SqlSession session = factory.openSession()) {
      missing = session.getMapper(DatasetImportMapper.class).listMissingFormat();
    }
    total = missing.size();
    LOG.info("{}{} imports have no data format recorded", dryRun ? "Dry run. " : "", total);
    setStep(progress());

    for (var imp : missing) {
      checkIfCancelled();
      detect(imp);
      if (++processed % STEP_BATCH == 0) {
        setStep(progress());
      }
    }

    if (!dryRun) {
      // imports without an archive, typically the earliest ones, from the same source files of a later import
      try (SqlSession session = factory.openSession(true)) {
        sameSource = session.getMapper(DatasetImportMapper.class).updateFormatFromSameSource();
      }
    }
    LOG.info("{}Detected the format of {} imports {}, {} formats differ from the dataset setting. {} imports without an archive, "
        + "{} with an undetectable one. {} imports took the format of an import of the same source files",
      dryRun ? "Dry run. " : "", detected.values().stream().mapToInt(i -> i).sum(), detected, conflicts, noArchive, undetected, sameSource);
  }

  private void detect(DatasetImportMapper.MissingFormat imp) {
    final int key = imp.getDatasetKey();
    final int attempt = imp.getAttempt();
    File archive = nCfg.archive(key, attempt);
    if (!archive.exists()) {
      noArchive++;
      LOG.debug("No archive stored for import {} of dataset {}", attempt, key);
      return;
    }
    Optional<DataFormat> format;
    try {
      format = DataFormatDetector.detectArchive(archive);
    } catch (Exception e) {
      LOG.warn("Failed to read archive {} of import {} of dataset {}", archive, attempt, key, e);
      format = Optional.empty();
    }
    if (format.isEmpty()) {
      undetected++;
      LOG.info("Cannot detect the format of import {} of dataset {} from {}", attempt, key, archive);
      return;
    }
    DataFormat df = format.get();
    detected.merge(df, 1, Integer::sum);
    if (imp.getSetting() != null && !imp.getSetting().equals(PermissiveEnumSerde.enumValueName(df))) {
      conflicts++;
      LOG.info("Import {} of dataset {} detected as {}, but the dataset is configured as {}", attempt, key, df, imp.getSetting());
    }
    if (!dryRun) {
      try (SqlSession session = factory.openSession(true)) {
        session.getMapper(DatasetImportMapper.class).updateFormat(key, attempt, df);
      }
    }
  }

  @Override
  protected void onFinish() {
    setStep(progress());
  }

  private String progress() {
    return (dryRun ? "dry run, " : "") + "processed " + processed + " of " + total;
  }
}
