package life.catalogue.command;

import life.catalogue.api.search.DatasetSearchRequest;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.dao.NameUsageArchiver;
import life.catalogue.db.mapper.ArchivedNameUsageMatchMapper;
import life.catalogue.db.mapper.DatasetMapper;

import java.util.List;

import org.apache.ibatis.session.SqlSession;

import net.sourceforge.argparse4j.inf.Subparser;

/**
 * Command that rebuilds the name usage archive for one or all projects based on existing id reports.
 */
public class ArchiveCmd extends AbstractMybatisCmd {
  private static final String ARG_KEY = "key";

  private NameUsageArchiver archiver;

  public ArchiveCmd() {
    super("archive", true, "Archive name usages for a single or all projects.");
  }
  
  @Override
  public void configure(Subparser subparser) {
    super.configure(subparser);
    // Adds import options
    subparser.addArgument("--"+ARG_KEY, "-k")
        .dest(ARG_KEY)
        .type(Integer.class)
        .required(false)
        .help("dataset key of the project to archive. If not given all projects are archived.");
  }

  @Override
  public void execute() throws Exception {
    archiver = new NameUsageArchiver(factory);

    Integer projectKey = ns.get(ARG_KEY);
    if (projectKey != null) {
      archiver.rebuildProject(projectKey, true);

    } else {
      List<Integer> projects;
      try (SqlSession session = factory.openSession()) {
        DatasetMapper dm = session.getMapper(DatasetMapper.class);
        var req = new DatasetSearchRequest();
        req.setOrigin(List.of(DatasetOrigin.PROJECT));
        req.setSortBy(DatasetSearchRequest.SortBy.KEY);
        projects = dm.searchKeys(req, userKey);
      }

      System.out.println("Total number of projects found to rebuild: " + projects.size());
      for (var key : projects) {
        System.out.println("Rebuild name usage archive for project " + key);
        archiver.rebuildProject(key, false);
      }

      System.out.println("Copy all name matches for all archived projects");
      try (SqlSession session = factory.openSession(true)) {
        var amm = session.getMapper(ArchivedNameUsageMatchMapper.class);
        var matches = amm.createAllMatches();
        System.out.println("Copied "+matches+" name matches");
      }
    }
    System.out.println("Archive rebuild completed.");
  }
}
