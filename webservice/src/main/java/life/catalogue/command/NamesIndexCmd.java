package life.catalogue.command;

import com.zaxxer.hikari.HikariDataSource;
import io.dropwizard.setup.Bootstrap;
import life.catalogue.WsServerConfig;
import life.catalogue.db.MybatisFactory;
import life.catalogue.matching.NameIndex;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class NamesIndexCmd extends AbstractPromptCmd {
  private static final Logger LOG = LoggerFactory.getLogger(NamesIndexCmd.class);
  private static final String ARG_THREADS = "t";

  public NamesIndexCmd() {
    super("nidx", "Rebuilt names index and rematch all datasets");

  }

  @Override
  public void configure(Subparser subparser) {
    super.configure(subparser);
    // Adds indexing options
    subparser.addArgument("-"+ ARG_THREADS)
      .dest(ARG_THREADS)
      .type(Integer.class)
      .required(false)
      .help("number of threads to use for rematching. Defaults to 1");
  }

  private static String indexNameToday(){
    String date = DateTimeFormatter.ISO_DATE.format(LocalDate.now());
    return "col-" + date;
  }

  @Override
  public void execute(Bootstrap<WsServerConfig> bootstrap, Namespace namespace, WsServerConfig cfg) throws Exception {
    try (HikariDataSource dataSource = cfg.db.pool()) {
      SqlSessionFactory factory = MybatisFactory.configure(dataSource, "namesIndexCmd");
      //TODO: setup new index & new matching tables...
      NameIndex ni = null;
      LOG.warn("Rebuilt names index and rematch all datasets with data");
      // kill names index
      ni.reset();
    }
  }

}
