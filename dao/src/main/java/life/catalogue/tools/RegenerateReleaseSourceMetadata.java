package life.catalogue.tools;

import com.zaxxer.hikari.HikariDataSource;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.dao.DatasetProjectSourceDao;
import life.catalogue.db.MybatisFactory;
import life.catalogue.db.PgConfig;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.ProjectSourceMapper;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import java.util.concurrent.atomic.AtomicInteger;

public class RegenerateReleaseSourceMetadata implements AutoCloseable {

  final SqlSessionFactory factory;
  final HikariDataSource dataSource;
  final Dataset release;
  final Dataset project;

  public RegenerateReleaseSourceMetadata(int releaseKey, PgConfig cfg) {
    dataSource = cfg.pool();
    factory = MybatisFactory.configure(dataSource, "tools");
    DatasetInfoCache.CACHE.setFactory(factory);

    try (SqlSession session = factory.openSession()) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      release = dm.get(releaseKey);
      if (release.getOrigin() != DatasetOrigin.RELEASED) {
        throw new IllegalArgumentException("Dataset key "+releaseKey+" is not a release!");
      }
      project = dm.get(release.getSourceKey());
    }
  }

  public void run(){
    System.out.printf("%s: %s\n\n", release.getKey(), release.getCitation());
    DatasetProjectSourceDao dao = new DatasetProjectSourceDao(factory);
    //show(dao);
    update(dao);
  }

  void show(DatasetProjectSourceDao dao){
    dao.list(project.getKey(), release).forEach(d -> {
      System.out.printf("%s: %s\n", d.getKey(), d.getCitation());
    });
  }

  void update(DatasetProjectSourceDao dao) {
    try (SqlSession session = factory.openSession(false)) {
      ProjectSourceMapper psm = session.getMapper(ProjectSourceMapper.class);
      int cnt =psm.deleteByProject(release.getKey());
      session.commit();
      System.out.printf("Deleted %s old source metadata records\n", cnt);

      AtomicInteger counter = new AtomicInteger(0);
      dao.list(project.getKey(), release).forEach(d -> {
        counter.incrementAndGet();
        System.out.printf("%s: %s\n", d.getKey(), d.getCitation());
        psm.create(release.getKey(), d);
      });
      session.commit();
      System.out.printf("Created %s new source metadata records\n", counter);
    }
  }

  public void close() {
    dataSource.close();
  }

  public static void main(String[] args) {
    PgConfig cfg = new PgConfig();
    cfg.host = "";
    cfg.database = "col";
    cfg.user = "col";
    cfg.password = "";
    try (RegenerateReleaseSourceMetadata reg = new RegenerateReleaseSourceMetadata(2230,cfg)) {
      reg.run();
    }
  }
}
