package life.catalogue.db.mapper;

import life.catalogue.api.RandomUtils;
import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.Origin;
import life.catalogue.db.CRUD;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;

import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DatasetPartitionMapperTest extends MapperTestBase<DatasetPartitionMapper> {
  Random rnd = new Random();
  
  public DatasetPartitionMapperTest() {
    super(DatasetPartitionMapper.class);
  }
  
  @Test
  public void createDelete() {
    // we create the prov-cat partition in the InitMybatisRule
    mapper().delete(Datasets.COL, DatasetOrigin.PROJECT);
    mapper().create(Datasets.COL, DatasetOrigin.PROJECT);
    mapper().attach(Datasets.COL, DatasetOrigin.PROJECT);

    mapper().delete(Datasets.COL, DatasetOrigin.PROJECT);
    mapper().create(Datasets.COL, DatasetOrigin.PROJECT);
    mapper().attach(Datasets.COL, DatasetOrigin.PROJECT);

    mapper().delete(Datasets.COL, DatasetOrigin.PROJECT);
  }

  @Test
  public void updateCounter() {
    int x = mapper().updateUsageCounter(TestDataRule.APPLE.key);
    assertEquals(4, x);
  }

  /**
   * Attaching requires an AccessExclusiveLock on the partitioned tables
   * which often leads to deadlocks, see https://github.com/Sp2000/colplus-backend/issues/387
   */
  @Test
  public void concurrentAttach() throws Exception {
    
    mapper().delete(Datasets.COL, DatasetOrigin.PROJECT);
    mapper().create(Datasets.COL, DatasetOrigin.PROJECT);
    mapper().attach(Datasets.COL, DatasetOrigin.PROJECT);

    commit();

    // run continuous ref and name imports
    ContinuousInserter<Reference> refIns = new ContinuousInserter<Reference>(PgSetupRule.getSqlSessionFactory(),
        Reference.class, ReferenceMapper.class, this::genRef);
    Thread tr = new Thread(refIns);

    ContinuousInserter<Name> nameIns = new ContinuousInserter<Name>(PgSetupRule.getSqlSessionFactory(),
        Name.class, NameMapper.class, this::genName);
    Thread tn = new Thread(nameIns);
    
    try {
      tr.start();
      tn.start();
  
      // wait a little so the threads get started
      TimeUnit.SECONDS.sleep(1);
      // now try to attach a different physical table to the same partition that we insert into
      final int datasetKey = TestDataRule.APPLE.key;
      System.out.println("Delete partition " + datasetKey);
      mapper().delete(datasetKey, DatasetOrigin.EXTERNAL);
      mapper().create(datasetKey, DatasetOrigin.EXTERNAL);
      commit();
      System.out.println("Try to attach the partition " + datasetKey);
      mapper().attach(datasetKey, DatasetOrigin.EXTERNAL);
      commit();
      System.out.println("Attached!!!");
  
    } finally {
      refIns.stop();
      nameIns.stop();
      tr.join();
      tn.join();
      System.out.println("All threads finished");
    }
  }
  
  private Reference genRef() {
    Reference r = gen(new Reference());
    r.setCitation(RandomUtils.randomLatinString(120));
    r.setYear(rnd.nextInt(2100));
    return r;
  }
  
  private Name genName() {
    Name n = gen(new Name());
    n.setType(NameType.SCIENTIFIC);
    n.setOrigin(Origin.SOURCE);
    n.setRank(Rank.SPECIES);
    n.setScientificName(RandomUtils.randomSpecies());
    n.setAuthorship(RandomUtils.randomAuthor());
    n.setCode(NomCode.BOTANICAL);
    n.setRemarks(RandomUtils.randomLatinString(50));
    return n;
  }
  
  private static <T extends DSID & UserManaged> T gen(T obj){
    obj.setDatasetKey(Datasets.COL);
    obj.setId(UUID.randomUUID().toString());
    obj.applyUser(TestEntityGenerator.USER_USER);
    return obj;
  }
  
  static class ContinuousInserter<T extends DataEntity<DSID<String>>> implements Runnable {
    private final SqlSessionFactory factory;
    private final Class<? extends CRUD<DSID<String>, T>> mapperClass;
    private final Supplier<T> generator;
    private final Class<T> objClass;
    private long counter;
    AtomicBoolean stop = new AtomicBoolean(false);
    
    ContinuousInserter(SqlSessionFactory factory, Class<T> objClass, Class<? extends CRUD<DSID<String>, T>> mapperClass, Supplier<T> generator) {
      this.factory = factory;
      this.objClass = objClass;
      this.mapperClass = mapperClass;
      this.generator = generator;
    }
  
    public void stop() {
      stop.set(true);
    }
    
    @Override
    public void run() {
      try (SqlSession session = factory.openSession(ExecutorType.BATCH, false)) {
        CRUD<DSID<String>, T> mapper = session.getMapper(mapperClass);
        
        while (!stop.get()) {
          T obj = generator.get();
          mapper.create(obj);
          if (counter++ % 1000 == 0) {
            session.commit();
            System.out.println("Inserted "+counter + " " + objClass.getSimpleName());
          } else if (counter > 10000) {
            stop.set(true);
            System.out.println("Stop as we inserted all "+counter + " " + objClass.getSimpleName());
          }
        }
      }
    }
  }
  
}