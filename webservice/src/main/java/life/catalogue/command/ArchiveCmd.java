package life.catalogue.command;

import com.google.common.eventbus.EventBus;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.ExportRequest;
import life.catalogue.api.model.Page;
import life.catalogue.api.search.DatasetSearchRequest;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.concurrent.JobExecutor;
import life.catalogue.dao.ArchivedNameUsageFactory;
import life.catalogue.dao.DatasetExportDao;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.dao.UserDao;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.doi.service.DataCiteService;
import life.catalogue.doi.service.DatasetConverter;
import life.catalogue.doi.service.DoiService;
import life.catalogue.dw.mail.MailBundle;
import life.catalogue.exporter.ExportManager;
import life.catalogue.img.ImageService;
import life.catalogue.img.ImageServiceFS;
import life.catalogue.release.ProjectRelease;
import life.catalogue.release.PublicReleaseListener;

import net.sourceforge.argparse4j.inf.Subparser;

import org.apache.ibatis.session.SqlSession;

import javax.validation.Validation;
import javax.validation.Validator;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Command that rebuilds the name usage archive for one or all projects.
 */
public class ArchiveCmd extends AbstractMybatisCmd {
  private static final String ARG_KEY = "key";

  private ArchivedNameUsageFactory archiver;

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
  void execute() throws Exception {
    archiver = new ArchivedNameUsageFactory(factory);

    Integer projectKey = ns.get(ARG_KEY);
    if (projectKey != null) {
      archiver.rebuildProject(projectKey);

    } else {
      List<Dataset> projects;
      try (SqlSession session = factory.openSession()) {
        DatasetMapper dm = session.getMapper(DatasetMapper.class);
        var req = new DatasetSearchRequest();
        req.setOrigin(List.of(DatasetOrigin.MANAGED));
        req.setSortBy(DatasetSearchRequest.SortBy.KEY);
        final int limit = 1000;
        projects = dm.search(req, userKey, new Page(0, limit));
        if (projects.size()>=limit) {
          System.out.println("WARNING! There are more than "+limit+" projects. Only the first 1000 will be archived!");
        }
      }

      for (Dataset d : projects) {
        archiver.rebuildProject(d.getKey());
      }
    }
    System.out.println("Archive rebuild completed.");
  }
}
