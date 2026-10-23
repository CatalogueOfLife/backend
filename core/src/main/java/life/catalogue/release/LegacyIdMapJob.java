package life.catalogue.release;

import life.catalogue.api.exception.UnavailableException;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.JobPriority;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.concurrent.DatasetJob;
import life.catalogue.config.ReleaseConfig;
import life.catalogue.dao.TreeStreams;
import life.catalogue.exporter.ColReleaseExportJob;
import life.catalogue.interpreter.NameInterpreter;
import life.catalogue.matching.AbstractMatchingJob;
import life.catalogue.matching.MatchingUtils;
import life.catalogue.matching.UsageMatch;
import life.catalogue.matching.UsageMatcher;
import life.catalogue.matching.UsageMatcherFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.io.FileUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.univocity.parsers.tsv.TsvWriter;
import com.univocity.parsers.tsv.TsvWriterSettings;

import static life.catalogue.api.jackson.PermissiveEnumSerde.enumValueName;

/**
 * Maps the identifiers of a retired COL edition to the usages of a published COL release and writes them as a
 * gzipped TSV next to the monthly exports, e.g. monthly/2026-09-25_xr_col19-idmap.tsv.gz.
 * The legacy identifiers are the natural keys (md5 hashes) the annual checklist PHP apps used in their URLs.
 * A keys file lists them with the internal record id the edition's ChecklistBank dataset uses as its usage id,
 * see {@link ReleaseConfig.LegacyIdMap}. Every usage of that dataset is matched by name against the release.
 * Keys whose record is missing in the dataset are matched by the name and authorship given in the keys file.
 *
 * Each file is the record of one release and is never overwritten: if it exists already the job does nothing.
 */
public class LegacyIdMapJob extends DatasetJob {
  private static final Logger LOG = LoggerFactory.getLogger(LegacyIdMapJob.class);
  // how long to wait for the release matcher, which gets built when a release is published
  private static final long MATCHER_WAIT_MINUTES = 12 * 60;

  private final ReleaseConfig.LegacyIdMap map;
  private final File colDownloadDir;
  private final SqlSessionFactory factory;
  private final UsageMatcherFactory matcherFactory;
  private final NameInterpreter interpreter = new NameInterpreter(new DatasetSettings(), true);

  public LegacyIdMapJob(ReleaseConfig.LegacyIdMap map, int releaseKey, int userKey, File colDownloadDir,
                        SqlSessionFactory factory, UsageMatcherFactory matcherFactory) {
    super(releaseKey, userKey, JobPriority.LOW);
    this.map = map;
    this.colDownloadDir = colDownloadDir;
    this.factory = factory;
    this.matcherFactory = matcherFactory;
    this.dataset = loadDataset(factory, releaseKey);
    if (!dataset.getOrigin().isRelease() || dataset.getIssued() == null) {
      throw new IllegalArgumentException("Dataset " + releaseKey + " is no release with a release date");
    }
    this.logToFile = true;
  }

  /**
   * @return the mapping file of the given release, next to its monthly exports
   */
  public static File file(File colDownloadDir, Dataset release, String mapName) {
    String iso = DateTimeFormatter.ISO_DATE.format(release.getIssued().getDate());
    return new File(colDownloadDir, "monthly/" + iso + "_" + ColReleaseExportJob.prefix(release.getOrigin()) + mapName + "-idmap.tsv.gz");
  }

  public File file() {
    return file(colDownloadDir, dataset, map.name);
  }

  @Override
  public Object getParams() {
    return map.name;
  }

  @Override
  public boolean isDuplicate(BackgroundJob other) {
    return other instanceof LegacyIdMapJob o && o.datasetKey == datasetKey && o.map.name.equals(map.name);
  }

  @Override
  public void execute() throws Exception {
    super.execute();
    final File target = file();
    if (target.exists()) {
      LOG.info("{} id mapping for release {} exists already at {}", map.name, datasetKey, target);
      return;
    }
    LOG.info("Load {} keys from {}", map.name, map.keys);
    final Keys keys = Keys.load(map.keys);
    LOG.info("Loaded {} {} keys", keys.size, map.name);

    final UsageMatcher matcher = awaitMatcher();
    final File tmp = new File(target.getParentFile(), "." + target.getName() + "." + getKey());
    try {
      FileUtils.forceMkdirParent(tmp);
      final MatchingUtils utils = new MatchingUtils(matcherFactory.getNameIndex());
      final Function<SimpleNameClassified<SimpleName>, UsageMatch> matchFunc = sn -> AbstractMatchingJob.interpretAndMatch(
        sn, MatchingUtils.toSimpleNameCached(sn.getClassification()), new IssueContainer.Simple(), false, interpreter, utils, matcher
      );
      try (var out = new GZIPOutputStream(new FileOutputStream(tmp));
           SqlSession session = factory.openSession()
      ) {
        try (var usages = TreeStreams.dataset(session, TreeTraversalParameter.dataset(map.datasetKey))) {
          write(map.name, keys, usages, () -> keys.stream(map.keys), matchFunc, out);
        }
      }
      checkIfCancelled();
      Files.move(tmp.toPath(), target.toPath());
      LOG.info("Wrote {} id mapping for release {} to {}", map.name, datasetKey, target);

    } catch (FileAlreadyExistsException e) {
      // the other app of a blue-green deploy got the same publish event
      LOG.info("{} id mapping for release {} was written meanwhile to {}", map.name, datasetKey, target);
    } finally {
      FileUtils.deleteQuietly(tmp);
      matcher.close();
    }
  }

  private UsageMatcher awaitMatcher() throws IOException, InterruptedException {
    final long until = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(MATCHER_WAIT_MINUTES);
    while (true) {
      try {
        return matcherFactory.persistent(datasetKey);
      } catch (UnavailableException e) {
        if (System.currentTimeMillis() > until) {
          throw e;
        }
        LOG.info("Wait for the matcher of release {}: {}", datasetKey, e.getMessage());
        TimeUnit.MINUTES.sleep(1);
        checkIfCancelled();
      }
    }
  }

  /**
   * Writes the mapping as TSV with a header row.
   * @param usages the usages of the legacy dataset, keyed by the internal record ids of the keys
   * @param keyRows all rows of the keys file, to match the keys whose record is missing in usages
   */
  @VisibleForTesting
  static void write(String prefix, Keys keys, Stream<SimpleNameClassified<SimpleName>> usages, KeyRowSupplier keyRows,
                    Function<SimpleNameClassified<SimpleName>, UsageMatch> matcher, OutputStream out) throws IOException {
    var settings = new TsvWriterSettings();
    var writer = new TsvWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8), settings);
    writer.writeHeaders(prefix + "ID", prefix + "InternalID", prefix + "Status", prefix + "Rank", prefix + "ScientificName", prefix + "Authorship",
      "matchType", "ID", "rank", "status", "scientificName", "authorship", "acceptedID", "acceptedScientificName", "acceptedAuthorship"
    );
    final BitSet seen = new BitSet(keys.size);
    final long[] counts = new long[2]; // keys written, keys matched
    usages.forEach(sn -> {
      int idx = keys.first(sn.getId());
      if (idx >= 0) {
        var m = matcher.apply(sn);
        final int id = keys.ids[idx];
        for (; idx < keys.size && keys.ids[idx] == id; idx++) {
          seen.set(idx);
          writer.writeRow(row(keys.hash(idx), sn, m, counts));
        }
        if (counts[0] % 100_000 == 0) {
          LOG.info("Mapped {} out of {} keys so far", counts[1], counts[0]);
        }
      }
    });
    LOG.info("Mapped {} out of {} keys with a usage in the legacy dataset", counts[1], counts[0]);

    // keys without a usage in the dataset: match the name given in the keys file
    long before = counts[0];
    try (var rows = keyRows.get()) {
      var iter = rows.iterator();
      int idx = 0;
      while (iter.hasNext()) {
        var row = iter.next();
        if (!seen.get(idx)) {
          var sn = new SimpleNameClassified<>(SimpleName.sn(row.name, row.authorship));
          sn.setId(String.valueOf(row.id));
          sn.setRank(null); // unknown, not unranked
          sn.setStatus(row.status);
          sn.setClassification(List.of());
          writer.writeRow(row(row.hash, sn, matcher.apply(sn), counts));
        }
        idx++;
      }
    }
    LOG.info("Mapped {} keys in total, {} without a usage in the legacy dataset", counts[1], counts[0] - before);
    writer.flush();
  }

  private static String[] row(String hash, SimpleName sn, UsageMatch m, long[] counts) {
    counts[0]++;
    var row = new String[15];
    row[0] = hash;
    row[1] = sn.getId();
    row[2] = str(sn.getStatus());
    row[3] = str(sn.getRank());
    row[4] = sn.getName();
    row[5] = sn.getAuthorship();
    row[6] = str(m.type);
    if (m.usage != null) {
      counts[1]++;
      row[7] = m.usage.getId();
      row[8] = str(m.usage.getRank());
      row[9] = str(m.usage.getStatus());
      row[10] = m.usage.getName();
      row[11] = m.usage.getAuthorship();
      if (m.usage.getStatus() != null && m.usage.getStatus().isSynonym() && !m.usage.getClassification().isEmpty()) {
        var acc = m.usage.getClassification().get(0);
        row[12] = acc.getId();
        row[13] = acc.getName();
        row[14] = acc.getAuthorship();
      }
    }
    return row;
  }

  private static String str(Enum<?> val) {
    return val == null ? null : enumValueName(val);
  }

  @FunctionalInterface
  interface KeyRowSupplier {
    Stream<KeyRow> get() throws IOException;
  }

  record KeyRow(String hash, int id, TaxonomicStatus status, String name, String authorship) {}

  /**
   * The legacy keys held in memory as compactly as possible, about 4 million for COL19:
   * internal record ids in ascending order with their md5 hash as two longs.
   */
  static class Keys {
    int size;
    int[] ids = new int[1024];
    long[] hashes = new long[2048];

    /**
     * Reads a keys TSV with a header row and the columns hash, id, status, scientificName, authorship,
     * sorted by id. Files ending in .gz are decompressed.
     */
    static Keys load(File file) throws IOException {
      Keys keys = new Keys();
      try (var rows = stream(file)) {
        rows.forEach(keys::add);
      }
      return keys;
    }

    static Stream<KeyRow> stream(File file) throws IOException {
      InputStream in = new FileInputStream(file);
      if (file.getName().endsWith(".gz")) {
        in = new GZIPInputStream(in, 65536);
      }
      var br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
      return br.lines().skip(1).filter(l -> !l.isBlank()).map(Keys::parse).onClose(() -> {
        try {
          br.close();
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      });
    }

    private static KeyRow parse(String line) {
      String[] cols = line.split("\t", -1);
      if (cols.length < 5) {
        throw new IllegalArgumentException("Bad legacy keys line: " + line);
      }
      return new KeyRow(cols[0].toLowerCase(), Integer.parseInt(cols[1]), status(cols[2]), cols[3], cols[4].isEmpty() ? null : cols[4]);
    }

    /**
     * @return the status of the keys file, which knows higher taxa besides the ChecklistBank status values
     */
    static TaxonomicStatus status(String status) {
      return switch (status) {
        case "higher taxon", "accepted" -> TaxonomicStatus.ACCEPTED;
        case "provisionally accepted" -> TaxonomicStatus.PROVISIONALLY_ACCEPTED;
        case "synonym" -> TaxonomicStatus.SYNONYM;
        case "ambiguous synonym" -> TaxonomicStatus.AMBIGUOUS_SYNONYM;
        case "misapplied" -> TaxonomicStatus.MISAPPLIED;
        default -> throw new IllegalArgumentException("Unknown legacy status " + status);
      };
    }

    void add(KeyRow row) {
      if (size > 0 && row.id < ids[size - 1]) {
        throw new IllegalArgumentException("Legacy keys must be sorted by id, but " + row.id + " follows " + ids[size - 1]);
      }
      if (row.hash.length() != 32) {
        throw new IllegalArgumentException("Legacy key is no md5 hash: " + row.hash);
      }
      if (size == ids.length) {
        ids = Arrays.copyOf(ids, size * 2);
        hashes = Arrays.copyOf(hashes, size * 4);
      }
      ids[size] = row.id;
      hashes[2 * size] = Long.parseUnsignedLong(row.hash, 0, 16, 16);
      hashes[2 * size + 1] = Long.parseUnsignedLong(row.hash, 16, 32, 16);
      size++;
    }

    /**
     * @return the index of the first key with the given internal record id or -1 if there is none
     */
    int first(String id) {
      int intId;
      try {
        intId = Integer.parseInt(id);
      } catch (NumberFormatException e) {
        return -1;
      }
      int idx = Arrays.binarySearch(ids, 0, size, intId);
      if (idx < 0) {
        return -1;
      }
      while (idx > 0 && ids[idx - 1] == intId) {
        idx--;
      }
      return idx;
    }

    String hash(int idx) {
      return String.format("%016x%016x", hashes[2 * idx], hashes[2 * idx + 1]);
    }
  }
}
