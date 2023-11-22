package life.catalogue.command;

import life.catalogue.WsServerConfig;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.NameMatch;
import life.catalogue.api.util.VocabularyUtils;
import life.catalogue.common.io.TabReader;
import life.catalogue.common.io.UnixCmdUtils;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.common.util.LoggingUtils;
import life.catalogue.concurrent.ExecutorUtils;
import life.catalogue.concurrent.NamedThreadFactory;
import life.catalogue.db.PgConfig;
import life.catalogue.db.SqlSessionFactoryWithPath;
import life.catalogue.matching.NameIndex;
import life.catalogue.matching.NameIndexFactory;
import life.catalogue.postgres.PgBinaryWriter;
import life.catalogue.postgres.PgCopyUtils;

import org.gbif.nameparser.api.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.jdbc.ScriptRunner;
import org.postgresql.jdbc.PgConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import de.bytefish.pgbulkinsert.row.SimpleRowWriter;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

public class NamesIndexCmd extends AbstractMybatisCmd {
  private static final Logger LOG = LoggerFactory.getLogger(NamesIndexCmd.class);
  private static final String ARG_THREADS = "t";
  private static final String ARG_FILE_ONLY = "file-only";
  private static final String ARG_LIMIT = "limit";
  private static final String BUILD_SCHEMA = "nidx";
  private static final String SCHEMA_SETUP = "nidx/rebuild-schema.sql";
  private static final String SCHEMA_POST = "nidx/rebuild-post.sql";
  private static final String SCHEMA_POST_CONSTRAINTS = "nidx/rebuild-post-constraints.sql";
  private static final String FILENAME_NAMES = "names.tsv";
  private static final String FILENAME_MATCHES = "matches";
  private static final String NAME_COLS = "scientific_name,authorship,rank,uninomial,genus,infrageneric_epithet,specific_epithet,infraspecific_epithet,cultivar_epithet,basionym_authors,basionym_ex_authors,basionym_year,combination_authors,combination_ex_authors,combination_year,sanctioning_author,type,code,notho,candidatus,sector_key,dataset_key,id";
  private static final SimpleRowWriter.Table MATCH_TABLE = new SimpleRowWriter.Table(BUILD_SCHEMA, "name_match", new String[] {
    "dataset_key",
      "sector_key",
      "name_id",
      "type",
      "index_id"
  });

  int threads = 4;
  File nidxFile;
  File buildDir;

  public NamesIndexCmd() {
    super("nidx", false, "Rebuilt names index and rematch all names");

  }

  @Override
  public void configure(Subparser subparser) {
    super.configure(subparser);
    // Adds indexing options
    subparser.addArgument("-"+ ARG_THREADS)
      .dest(ARG_THREADS)
      .type(Integer.class)
      .required(false)
      .help("number of threads to use for rematching. Defaults to " + threads);
    subparser.addArgument("--"+ ARG_FILE_ONLY)
       .dest(ARG_FILE_ONLY)
       .type(Boolean.class)
       .required(false)
       .setDefault(false)
       .help("If true only rebuild the namesindex file, but do not rematch the database.");
    subparser.addArgument("--"+ ARG_LIMIT)
       .dest(ARG_LIMIT)
       .type(Integer.class)
       .required(false)
       .help("Optional limit of names to export for tests");
  }

  @Override
  public String describeCmd(Namespace namespace, WsServerConfig cfg) {
    return String.format("Rebuilt names index and rematch all datasets with data in pg schema %s in db %s.\n", BUILD_SCHEMA, cfg.db.database);
  }

  private static File indexBuildFile(WsServerConfig cfg) throws IOException {
    File f = null;
    if (cfg.namesIndexFile != null) {
      f = new File(cfg.namesIndexFile.getParent(), "nidx-build");
      if (f.exists()) {
        throw new IllegalStateException("NamesIndex file already exists: " + f.getAbsolutePath());
      }
      FileUtils.createParentDirectories(f);
      System.out.println("Creating new names index at " + f.getAbsolutePath());
    } else {
      System.out.println("Creating new in memory names index");
    }
    return f;
  }

  @Override
  public void execute() throws Exception {
    nidxFile = indexBuildFile(cfg);
    buildDir = cfg.normalizer.scratchDir("nidx-build");
    FileUtils.deleteQuietly(nidxFile);

    if (ns.getBoolean(ARG_FILE_ONLY)) {
      rebuildFileOnly();
    } else {
      rematchAll();
    }
  }

  private void rebuildFileOnly() throws Exception {
    LOG.info("Rebuild index file at {}", nidxFile);
    NameIndex ni = NameIndexFactory.persistentOrMemory(nidxFile, factory, AuthorshipNormalizer.INSTANCE, true);
    ni.start();
    LOG.info("Done rebuilding index file at {}", nidxFile);
  }

  private void rematchAll() throws Exception {
    if (ns.getInt(ARG_THREADS) != null) {
      threads = ns.getInt(ARG_THREADS);
      Preconditions.checkArgument(threads > 0, "Needs at least one matcher thread");
    }
    File out = new File(buildDir, FILENAME_NAMES);
    LOG.warn("Rebuilt names index at {} and rematch all names with {} threads using build folder {} and pg schema {}", nidxFile, threads, out, BUILD_SCHEMA);
    // use a factory that changes the default pg search_path to "nidx" so we don't interfere with the index currently live
    factory = new SqlSessionFactoryWithPath(factory, BUILD_SCHEMA);

    LOG.info("Prepare pg schema {}", BUILD_SCHEMA);
    try (Connection c = dataSource.getConnection()) {
      ScriptRunner runner = PgConfig.scriptRunner(c);
      runner.runScript(Resources.getResourceAsReader(SCHEMA_SETUP));
    }

    // setup new nidx using the session factory with the nidx schema - which has no names yet
    final NameIndex ni = NameIndexFactory.persistentOrMemory(nidxFile, factory, AuthorshipNormalizer.INSTANCE, false);
    ni.start();

    long total;
    if (out.exists()) {
      try(LineNumberReader lineNumberReader = new LineNumberReader(new FileReader(out))) {
        //Skip to last line
        lineNumberReader.skip(Long.MAX_VALUE);
        total = lineNumberReader.getLineNumber() + 1;
      }
      LOG.info("Use {} names from existing file {}", total, out);
    } else {
      // assert parent dir exists
      FileUtils.createParentDirectories(out);
      String limit = "";
      if (ns.getInt(ARG_LIMIT) != null) {
        limit = " LIMIT " + ns.getInt(ARG_LIMIT);
        LOG.info("Dumping {} names to {}", limit, out);
      } else {
        LOG.info("Dumping all names to {}", out);
      }
      try (Connection c = dataSource.getConnection()) {
        var pgc = c.unwrap(PgConnection.class);
        total = PgCopyUtils.dumpBinary(pgc, "SELECT " + NAME_COLS + " FROM name ORDER BY scientific_name, rank" + limit, out);
      }
    }

    final long size = (total / threads) +1;
    // calc exact number of files. With small numbers we can have the case that the last part is missing
    // e.g. total=6, threads=4, 3 files only as size cannot be 1.5 but is 2 instead
    final int parts = size * (threads-1) >= total ? threads-1 : threads;
    LOG.info("Splitting {} with {} records into {} parts with {} each", out, total, parts, size);
    UnixCmdUtils.split(out, size, 3);

    LOG.info("Matching all names from {}", out);
    ExecutorService exec = Executors.newFixedThreadPool(parts, new NamedThreadFactory("matcher"));
    for (int p = 0; p < parts; p++) {
      final int part = p;
      CompletableFuture.supplyAsync(() -> matchFile(part, ni), exec)
                       .exceptionally(ex -> {
                         LOG.error("Error matching names part {}", part, ex.getCause());
                         return null;
                       });
    }
    ExecutorUtils.shutdown(exec);
    LOG.info("Successfully rebuild names index with final size {}, rematching all {} names", ni.size(), total);

    LOG.info("Shutting down names index");
    ni.close();

    LOG.info("Inserting matches into postgres");
    try (Connection c = dataSource.getConnection()) {
      var pgc = c.unwrap(PgConnection.class);
      for (int p = 1; p < parts; p++) {
        File mf = part(FILENAME_MATCHES, p);
        LOG.info("  copy matches {}: {}", p, mf);
        PgCopyUtils.copyBinary(pgc, MATCH_TABLE.getTable(), Arrays.asList(MATCH_TABLE.getColumns()), mf);
        c.commit();
      }
    }

    LOG.info("Building postgres indices for new names index");
    try (Connection c = dataSource.getConnection()) {
      c.setAutoCommit(true);
      ScriptRunner runner = PgConfig.scriptRunner(c, false);
      runner.runScript(Resources.getResourceAsReader(SCHEMA_POST));
      // keep match constraints separate from indices so indices stay and do not rollback in case users have modified data since we started the command
      runner.runScript(Resources.getResourceAsReader(SCHEMA_POST_CONSTRAINTS));
    }
    LOG.info("Names index rebuild completed. Please put the new index (postgres & file) live manually");
  }

  private File part(String name, int part) {
    return new File(buildDir, String.format(name+"%03d", part));
  }

  private static class FileMatcher {
    private final NameIndex ni;
    private final File in;
    private final File out;
    private int counter = 0;
    private int error = 0;
    private int cached = 0;
    private int nomatch = 0;
    public FileMatcher(NameIndex ni, File in, File out) {
      this.ni = ni;
      this.in = in;
      this.out = out;
    }

    private static String str(Object obj) {
      return obj == null ? null : obj.toString();
    }

    public void matchAll() {
      try (TabReader reader = TabReader.tab(in, StandardCharsets.UTF_8, 0);
           var writer = new PgBinaryWriter(new FileOutputStream(out))
      ) {
        String lastLabel=null;
        Rank lastRank=null;
        NameMatch lastMatch=null;
        final Function<String, String> nullCharacterHandler = (val) -> val;
        for (String[] row : reader) {
          counter++;
          try {
            Name n = buildName(row);
            // matched the same name before already? the input file is sorted!
            NameMatch m;
            if (lastRank == n.getRank() && Objects.equals(lastLabel, n.getLabel())) {
              m = lastMatch;
              cached++;
            } else {
              m = ni.match(n, true, false);
              lastMatch = m;
              lastLabel=n.getLabel();
              lastRank = n.getRank();
            }
            writer.startRow(MATCH_TABLE.getColumns().length);
            writer.writeInt(n.getDatasetKey());
            writer.writeInteger(n.getSectorKey());
            writer.writeText(n.getId());
            writer.writeEnum(m.getType());
            writer.writeInteger(m.getNameKey());

            if (!m.hasMatch()) {
              nomatch++;
            }
          } catch (Exception e) {
            error++;
            LOG.error("Failed to match name {} from {}. {} total errors", counter, in, error, e);
          }
          if (counter % 100000 == 0) {
            LOG.info("Matched {} names from {}. {}% cached, {} errors, {} have no match", counter, in, 100*cached/counter, error, nomatch);
          }
        }
        LOG.info("Matched all {} names from {}. {}% cached, {} errors, {} have no match", counter, in, 100*cached/counter, error, nomatch);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  // 0 scientific_name,authorship,rank,uninomial,genus,infrageneric_epithet,specific_epithet,infraspecific_epithet,cultivar_epithet,
  // 9 basionym_authors,basionym_ex_authors,basionym_year,combination_authors,combination_ex_authors,combination_year,sanctioning_author,
  // 16 type,code,notho,candidatus,sector_key,dataset_key,id";
  @VisibleForTesting
  protected static Name buildName(String[] row) {
    Name n = Name.newBuilder()
                 .scientificName(str(row[0]))
                 .authorship(str(row[1]))
                 .rank(enumVal(Rank.class, row[2]))
                 .uninomial(str(row[3]))
                 .genus(str(row[4]))
                 .infragenericEpithet(str(row[5]))
                 .specificEpithet(str(row[6]))
                 .infraspecificEpithet(str(row[7]))
                 .cultivarEpithet(str(row[8]))
                 .basionymAuthorship(authors(row[9],row[10],row[11]))
                 .combinationAuthorship(authors(row[12],row[13],row[14]))
                 .sanctioningAuthor(str(row[15]))
                 .type(enumVal(NameType.class, row[16]))
                 .code(enumVal(NomCode.class, row[17]))
                 .notho(enumVal(NamePart.class, row[18]))
                 .candidatus(bool(row[19]))
                 .sectorKey(intVal(row[20]))
                 .datasetKey(intVal(row[21]))
                 .id(str(row[22]))
                 .build();
    return n;
  }

  static boolean bool(String x) {
    return x != null && x.equals("t");
  }
  static String str(String x) {
    return x == null || x.isEmpty() ? null : x;
  }
  static Integer intVal(String x) {
    return x == null || x.isEmpty() ? null : Integer.parseInt(x);
  }
  static <T extends Enum<?>> T enumVal(Class < T > clazz, String x) {
    return x == null || x.isEmpty() ? null : VocabularyUtils.lookupEnum(x, clazz);
  }
  static Authorship authors(String auth, String ex, String year) {
    return new Authorship(authList(auth), authList(ex), str(year));
  }
  static List<String> authList(String auth) {
    if (auth != null && auth.length() > 2) {
      return Arrays.asList(StringUtils.split(auth.substring(1, auth.length() - 1), ','));
    }
    return null;
  }

  private FileMatcher matchFile(int part, NameIndex ni) throws RuntimeException {
    LoggingUtils.setDatasetMDC(-1 * part, getClass());
    File in = part(FILENAME_NAMES, part);
    File out = part(FILENAME_MATCHES, part);
    FileMatcher matcher = new FileMatcher(ni, in, out);
    matcher.matchAll();

    LoggingUtils.removeDatasetMDC();
    return matcher;
  }

}
