package life.catalogue.command;

import life.catalogue.WsServerConfig;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.NameMatch;
import life.catalogue.api.util.VocabularyUtils;
import life.catalogue.common.io.TabReader;
import life.catalogue.common.io.TabWriter;
import life.catalogue.common.io.UnixCmdUtils;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.common.util.LoggingUtils;
import life.catalogue.concurrent.ExecutorUtils;
import life.catalogue.concurrent.NamedThreadFactory;
import life.catalogue.db.PgConfig;
import life.catalogue.db.SqlSessionFactoryWithPath;
import life.catalogue.matching.NameIndex;
import life.catalogue.matching.NameIndexFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import life.catalogue.postgres.PgCopyUtils;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.jdbc.ScriptRunner;

import org.gbif.nameparser.api.*;

import org.postgresql.jdbc.PgConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

public class NamesIndexCmd extends AbstractMybatisCmd {
  private static final Logger LOG = LoggerFactory.getLogger(NamesIndexCmd.class);
  private static final String ARG_THREADS = "t";
  private static final String ARG_FILE_ONLY = "file-only";
  private static final String BUILD_SCHEMA = "nidx";
  private static final String SCHEMA_SETUP = "nidx/rebuild-schema.sql";
  private static final String SCHEMA_POST = "nidx/rebuild-post.sql";
  private static final String SCHEMA_POST_CONSTRAINTS = "nidx/rebuild-post-constraints.sql";
  private static final String FILENAME_NAMES = "names.tsv";
  private static final String FILENAME_MATCHES = "matches.tsv";
  private static final String NAME_COLS = "scientific_name,authorship,rank,uninomial,genus,infrageneric_epithet,specific_epithet,infraspecific_epithet,cultivar_epithet,basionym_authors,basionym_ex_authors,basionym_year,combination_authors,combination_ex_authors,combination_year,sanctioning_author,type,code,notho,candidatus,dataset_key,id";

  int threads = 4;

  public NamesIndexCmd() {
    super("nidx", false, "Rebuilt names index and rematch all datasets");

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
  }

  @Override
  public String describeCmd(Namespace namespace, WsServerConfig cfg) {
    return String.format("Rebuilt names index and rematch all datasets with data in pg schema %s in db %s.\n", BUILD_SCHEMA, cfg.db.database);
  }

  private static File indexBuildFile(WsServerConfig cfg){
    File f = null;
    if (cfg.namesIndexFile != null) {
      f = new File(cfg.namesIndexFile.getParent(), "nidx-build");
      if (f.exists()) {
        throw new IllegalStateException("NamesIndex file already exists: " + f.getAbsolutePath());
      }
      f.getParentFile().mkdirs();
      System.out.println("Creating new names index at " + f.getAbsolutePath());
    } else {
      System.out.println("Creating new in memory names index");
    }
    return f;
  }

  @Override
  public void execute() throws Exception {
    if (ns.getBoolean(ARG_FILE_ONLY)) {
      rebuildFileOnly();
    } else {
      rematchAll();
    }
  }

  private void rebuildFileOnly() throws Exception {
    var nidxF = indexBuildFile(cfg);
    LOG.info("Rebuild index file at {}", nidxF);
    NameIndex ni = NameIndexFactory.persistentOrMemory(nidxF, factory, AuthorshipNormalizer.INSTANCE, true);
    ni.start();
    LOG.info("Done rebuilding index file at {}", nidxF);
  }

  private void rematchAll() throws Exception {
    if (ns.getInt(ARG_THREADS) != null) {
      threads = ns.getInt(ARG_THREADS);
      Preconditions.checkArgument(threads > 0, "Needs at least one matcher thread");
    }
    LOG.warn("Rebuilt names index and rematch all datasets with data in pg schema {} with {} threads", BUILD_SCHEMA, threads);
    // use a factory that changes the default pg search_path to "nidx" so we don't interfere with the index currently live
    factory = new SqlSessionFactoryWithPath(factory, BUILD_SCHEMA);

    LOG.info("Prepare pg schema {}", BUILD_SCHEMA);
    try (Connection c = dataSource.getConnection()) {
      ScriptRunner runner = PgConfig.scriptRunner(c);
      runner.runScript(Resources.getResourceAsReader(SCHEMA_SETUP));
    }

    NameIndex ni = NameIndexFactory.persistentOrMemory(indexBuildFile(cfg), factory, AuthorshipNormalizer.INSTANCE, false);
    ni.start();

    File out = new File(buildDir(), FILENAME_NAMES);
    LOG.info("Dumping all names to {}", out);
    long total;
    try (Connection c = dataSource.getConnection()) {
      var pgc = c.unwrap(PgConnection.class);
      total = PgCopyUtils.dumpTSVNoHeader(pgc, "SELECT " + NAME_COLS + " FROM name LIMIT 2500", out);
    }

    LOG.info("Sorting file {}", out);
    UnixCmdUtils.sortC(out, 0);

    LOG.info("Splitting {} with {} records into {} parts", out, total, threads);
    long size = total / threads;
    UnixCmdUtils.split(out, size, 3);

    LOG.info("Matching all names from {}", out);
    ExecutorService exec = Executors.newFixedThreadPool(threads, new NamedThreadFactory("dataset-matcher"));
    for (int p = 1; p < threads; p++) {
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
      for (int p = 1; p < threads; p++) {
        File mf = part(FILENAME_MATCHES, p);
        LOG.info("  copy matches {}: {}", p, mf);
        PgCopyUtils.copyTSV(pgc, BUILD_SCHEMA+".name_match", mf);
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

  private File buildDir() {
    return cfg.normalizer.scratchDir("nidx-build");
  }
  private File part(String name, int part) {
    return new File(buildDir(), String.format(name+"%03d", part));
  }

  private static class FileMatcher {
    private final NameIndex ni;
    private final File in;
    private final File out;
    private int counter = 0;
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
           var writer = TabWriter.fromFile(out)
      ) {
        String lastLabel=null;
        Rank lastRank=null;
        NameMatch lastMatch=null;
        // header row
        writer.write(new String[]{"dataset_key", "name_id", "type", "index_id"});
        for (String[] row : reader) {
          counter++;
          Name n = buildName(row);
          // matched the same name before already? the input file is sorted!
          NameMatch m;
          if (lastRank == n.getRank() && Objects.equals(lastLabel, n.getLabel())) {
            m = lastMatch;
          } else {
            m = ni.match(n, true, false);
            lastMatch = m;
            lastLabel=n.getLabel();
            lastRank = n.getRank();
          }
          String[] result = new String[]{str(n.getDatasetKey()), n.getId(), str(m.getType()), str(m.getNameKey())};
          writer.write(result);

          if (!m.hasMatch()) {
            nomatch++;
          }
          if (counter % 100000 == 0) {
            LOG.info("Matched {} names from {}. {} have no match", counter, in, nomatch);
          }
        }
        LOG.info("Matched all {} names from {}. {} have no match", counter, in, nomatch);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    // 0 scientific_name,authorship,rank,uninomial,genus,infrageneric_epithet,specific_epithet,infraspecific_epithet,cultivar_epithet,
    // 9 basionym_authors,basionym_ex_authors,basionym_year,combination_authors,combination_ex_authors,combination_year,sanctioning_author,
    // 16 type,code,notho,candidatus,dataset_key,id";
    private Name buildName(String[] row) {
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
         .datasetKey(intVal(row[20]))
         .id(str(row[21]))
         .build();
      return n;
    }
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
  static <T extends Enum<?>> T enumVal(Class<T> clazz, String x) {
    return x == null || x.isEmpty() ? null : VocabularyUtils.lookupEnum(x, clazz);
  }
  static Authorship authors(String auth, String ex, String year) {
    return new Authorship(authList(auth), authList(ex), str(year));
  }
  static List<String> authList(String auth) {
    if (auth != null && auth.length() > 2) {
      return List.of(StringUtils.split(auth.substring(1, auth.length()-1), ','));
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
