package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.db.SectorProcessable;

import java.util.concurrent.atomic.AtomicInteger;

public class SectorProcessableTestComponent {

  public static void test(SectorProcessable<?> mapper, DSID<Integer> key) throws Exception {
    //Partitioner.partition(SqlSessionFactoryRule.getSqlSessionFactory(), 998);
    //Partitioner.createManagedObjects(SqlSessionFactoryRule.getSqlSessionFactory(), 998);
    //Sector s = new Sector();

    AtomicInteger counter = new AtomicInteger();
    mapper.processSector(key).forEach(o -> counter.incrementAndGet());

    mapper.removeSectorKey(key);

    mapper.deleteBySector(key);
  }
}
