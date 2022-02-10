package life.catalogue.release;

import life.catalogue.api.model.Agent;
import life.catalogue.api.model.Dataset;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.db.MybatisFactory;
import life.catalogue.db.PgConfig;
import life.catalogue.db.mapper.*;

import java.util.List;
import java.util.regex.Pattern;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zaxxer.hikari.HikariDataSource;

public class UpdateAgentsTool implements AutoCloseable {
  private static final Logger LOG = LoggerFactory.getLogger(UpdateAgentsTool.class);

  final SqlSessionFactory factory;
  final HikariDataSource dataSource;
  final int userKey;

  public UpdateAgentsTool(PgConfig pgCfg, int userKey) {
    dataSource = pgCfg.pool();
    this.userKey = userKey;
    factory = MybatisFactory.configure(dataSource, "tools");
    DatasetInfoCache.CACHE.setFactory(factory);
  }

  /**
   * Read and update all agents from dataset metadata.
   */
  public void rebuildAgents(){
    System.out.println("Update all agents\n");

    try (SqlSession session = factory.openSession(true)) {
      rebuildAgents(session.getMapper(DatasetMapper.class));
      rebuildAgents(session.getMapper(DatasetArchiveMapper.class));
      rebuildAgents(session.getMapper(DatasetSourceMapper.class));
      rebuildAgents(session.getMapper(DatasetPatchMapper.class));
    }
  }

  private void rebuildAgents(DatasetAgentMapper mapper){
    System.out.printf("Update all agents with %s\n", mapper.getClass().getSimpleName());

    int counter = 0;
    for (Dataset d : mapper.listAgents()) {
      updateIds(d);
      mapper.updateAgents(d);
      if (++counter % 25 == 0) {
        System.out.printf("Updated all agents from %s datasets\n", counter);
      }
    }
    System.out.printf("Finished updating all agents from %s datasets\n", counter);
  }

  private void updateIds(Dataset d){
    updateIds(d.getKey(), d.getContact());
    updateIds(d.getKey(), d.getPublisher());
    updateIds(d.getKey(), d.getCreator());
    updateIds(d.getKey(), d.getEditor());
    updateIds(d.getKey(), d.getContributor());
  }

  private void updateIds(int datasetKey, List<Agent> as){
    if (as != null) {
      for (Agent a : as) {
        updateIds(datasetKey, a);
      }
    }
  }

  private void updateIds(int datasetKey, Agent a){
    if (a != null) {
      if (a.getOrcid() != null) {
        a.setOrcid(a.getOrcid());
        Pattern regex = Pattern.compile("^(\\d\\d\\d\\d-){3}\\d\\d\\d[\\dX]$");
        if (!regex.matcher(a.getOrcid()).find()) {
          LOG.warn("Bad ORCID {} found in {}: {}", a.getOrcid(), datasetKey, a);
        }
      }
      if (a.getRorid() != null) {
        a.setRorid(a.getRorid());
        Pattern regex = Pattern.compile("^0[a-z0-9]{6}\\d\\d$");
        if (!regex.matcher(a.getRorid()).find()) {
          LOG.warn("Bad RORID {} found in {}: {}", a.getRorid(), datasetKey, a);
        }
      }
    }
  }

  public void close() {
    dataSource.close();
  }

  public static void main(String[] args) {
    PgConfig cfg = new PgConfig();
    cfg.host = "pg1.catalogueoflife.org";
    cfg.database = "col";
    cfg.user = "col";
    cfg.password = "";

    try (UpdateAgentsTool reg = new UpdateAgentsTool(cfg, 101)) { // 101=markus
      reg.rebuildAgents();
    }
  }
}
