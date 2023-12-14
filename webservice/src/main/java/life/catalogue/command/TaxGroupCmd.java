package life.catalogue.command;

import life.catalogue.api.model.SimpleNameInDataset;
import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.concurrent.ExecutorUtils;
import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.NameMapper;
import life.catalogue.matching.TaxGroupAnalyzer;

import org.gbif.nameparser.api.Rank;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

import net.sourceforge.argparse4j.inf.Subparser;

public class TaxGroupCmd extends AbstractMybatisCmd {
  private static final Logger LOG = LoggerFactory.getLogger(TaxGroupCmd.class);
  private final static AtomicInteger COUNTER = new AtomicInteger(1);
  private static final String ARG_THREADS = "t";
  int threads = 8;

  public TaxGroupCmd() {
    super("taxgroup", "Creates a table for all names and fills it with the analyzed tax group for local db querying");
  }
  @Override
  public void configure(Subparser subparser) {
    super.configure(subparser);
    // Adds indexing options
    subparser.addArgument("-" + ARG_THREADS)
      .dest(ARG_THREADS)
      .type(Integer.class)
      .required(false)
      .help("number of threads to use for analyzing and writing. Defaults to " + threads);
  }

  @Override
  public void execute() throws Exception {
    init();

    ExecutorService exec = Executors.newFixedThreadPool(threads);
    try (SqlSession session = factory.openSession()) {
      LOG.info("Reading all names from postgres");
      var nm = session.getMapper(NameMapper.class);
      final List<SimpleNameInDataset> names = new ArrayList<>();
      PgUtils.consume(() -> nm.processAll(Rank.SUPRAGENERIC_NAME),sn -> {
        if (names.size() >= 10000) {
          exec.submit(new AnalysisJob(names));
          names.clear();
        }
        names.add(sn);
      });
      exec.submit(new AnalysisJob(names));
    }
    LOG.info("Waiting for all {} analysis jobs to complete", COUNTER.get());
    ExecutorUtils.shutdown(exec);
    LOG.info("Tax group analysis complete");
  }

  class AnalysisJob implements Runnable {
    private final int id;
    private final List<SimpleNameInDataset> names;
    private final TaxGroupAnalyzer tga = new TaxGroupAnalyzer();

    AnalysisJob(List<SimpleNameInDataset> names) {
      this.names = ImmutableList.copyOf(names);
      id = COUNTER.getAndIncrement();
      LOG.info("Created analyzer {} with {} names", id, names.size());
    }

    @Override
    public void run() {
      LOG.info("Starting analyzer {}", id);
      try (var con = cfg.db.connect();
           PreparedStatement pStmt = con.prepareStatement("INSERT INTO tax_groups (dataset_key, id, tg) VALUES (?, ?, ?::TAX_GROUP)")
      ) {
        con.setAutoCommit(false);
        for (var sn : names) {
          var tg = tga.analyze(sn);
          pStmt.setInt(1, sn.getDatasetKey());
          pStmt.setString(2, sn.getId());
          pStmt.setObject(3, tg == null ? null : tg.name());
          pStmt.execute();
        }
        con.commit();
        LOG.info("Finished analyzer {}/{} with {} names", id, COUNTER.get(), names.size());

      } catch (Exception e) {
        LOG.error("Error analyzing tax groups", e);
        throw new RuntimeException(e);
      }
    }
  }

  private void init() throws SQLException {
    LOG.info("prepare tables in postgres");
    try (var con = cfg.db.connect();
         var stmt = con.createStatement();
    ) {
      stmt.execute("DROP TABLE IF EXISTS tax_groups");
      stmt.execute("DROP TYPE IF EXISTS TAX_GROUP");
      StringBuilder sb = new StringBuilder();
      sb.append("CREATE TYPE TAX_GROUP AS ENUM (");
      boolean first = true;
      for (TaxGroup tg : TaxGroup.values()) {
        if (!first) {
          sb.append(",");
        }
        first = false;
        sb.append("'");
        sb.append(tg.name());
        sb.append("'");
      }
      sb.append(")");
      LOG.info(sb.toString());
      stmt.execute(sb.toString());
      stmt.execute("CREATE TABLE tax_groups (dataset_key INTEGER, id TEXT, tg TAX_GROUP)");
    }
  }

}
