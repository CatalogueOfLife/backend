package life.catalogue.command;

import life.catalogue.WsServerConfig;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.db.MybatisFactory;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.DatasetPatchMapper;
import life.catalogue.db.mapper.DatasetSourceMapper;
import life.catalogue.exporter.DatasetYamlWriter;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import com.zaxxer.hikari.HikariDataSource;

import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

/**
 * Command to exports metadata yaml files for all sources for a given project.
 */
public class ExportSourcesCmd extends AbstractPromptCmd {
  private static final String ARG_KEY = "key";

  private SqlSessionFactory factory;
  private HikariDataSource dataSource;
  private Dataset project;
  private Map<Integer, String> keys;
  private File expFolder;

  public ExportSourcesCmd() {
    super("expSources", "Export all source metadata for an existing project");
  }
  
  @Override
  public void configure(Subparser subparser) {
    super.configure(subparser);
    // Adds import options
    subparser.addArgument("--"+ARG_KEY, "-k")
        .dest(ARG_KEY)
        .type(Integer.class)
        .required(true)
        .help("dataset key of the project to export");
  }

  @Override
  public void execute(Bootstrap<WsServerConfig> bootstrap, Namespace namespace, WsServerConfig cfg) throws Exception {
    int projectKey = namespace.getInt(ARG_KEY);

    dataSource = cfg.db.pool();
    factory = MybatisFactory.configure(dataSource, "tools");
    DatasetInfoCache.CACHE.setFactory(factory);

    try (SqlSession session = factory.openSession()) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      project = dm.get(projectKey);
      if (project.getOrigin() != DatasetOrigin.MANAGED) {
        throw new IllegalArgumentException("Dataset key "+projectKey+" is not a project!");
      }
    }

    System.out.printf("Title for project %s: %s\n\n", project.getKey(), project.getTitle());
    export();
    System.out.println("Done.");
  }

  File yaml(int sourceKey, String kind) {
    File f = new File(expFolder, keys.get(sourceKey) + "/" + sourceKey + "-" + kind + ".yaml");
    f.getParentFile().mkdirs();
    return f;
  }

  void export() throws IOException {
    expFolder = new File("sources-" + project.getKey());

    try (SqlSession session = factory.openSession()) {
      DatasetSourceMapper psm = session.getMapper(DatasetSourceMapper.class);
      DatasetPatchMapper dpm = session.getMapper(DatasetPatchMapper.class);
      DatasetMapper dm = session.getMapper(DatasetMapper.class);

      System.out.print("Export sources:\n");
      for (var src : psm.listProjectSources(project.getKey())) {
        File srcFolder = new File(expFolder, ObjectUtils.coalesce(src.getAlias(), src.getKey().toString()));
        System.out.printf("  %s (%s): %s\n", src.getKey(), src.getAlias(), srcFolder.getAbsoluteFile());
        final String prefix = src.getKey() + "-";
        var d = dm.get(src.getKey());
        DatasetYamlWriter.write(d, new File(srcFolder, prefix+"latest.yaml"));
        // src differs?
        if (!Objects.equals(src, d) && !Objects.equals(src.getAttempt(), d.getAttempt())) {
          DatasetYamlWriter.write(src, new File(srcFolder, prefix+"archived.yaml"));
          try (var writer = UTF8IoUtils.writerFromFile(new File(srcFolder, "attempts.txt"))) {
            writer.append("latest=");
            writer.append(d.getAttempt().toString());
            writer.newLine();
            writer.append("archived=");
            writer.append(src.getAttempt().toString());
            writer.newLine();
          }
        }
        // patch?
        var patch = dpm.get(project.getKey(), src.getKey());
        // not all sources have a patch
        if (patch != null) {
          // nullify some empty values
          if (patch.getIdentifier() != null && patch.getIdentifier().isEmpty()) {
            patch.setIdentifier(null);
          }
          if (patch.getSource() != null && patch.getSource().isEmpty()) {
            patch.setSource(null);
          }
          if (patch.getContact() != null && patch.getContact().isEmpty()) {
            patch.setContact(null);
          }
          if (patch.getPublisher() != null && patch.getPublisher().isEmpty()) {
            patch.setPublisher(null);
          }
          DatasetYamlWriter.write(patch, new File(srcFolder, prefix+"patch.yaml"));
        }
      }
    }


  }

  public static void main(String[] args) throws Exception {
    WsServerConfig cfg = new WsServerConfig();
    cfg.db.host="localhost";
    cfg.db.database="datasets";
    cfg.db.user="postgres";
    cfg.db.password="";

    Namespace ns =  new Namespace(Map.of(
      ARG_KEY, 3
    ));
    ExportSourcesCmd cmd = new ExportSourcesCmd();
    cmd.execute(null, ns, cfg);
  }
}
