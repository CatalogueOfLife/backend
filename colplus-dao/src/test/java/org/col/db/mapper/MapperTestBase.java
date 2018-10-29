package org.col.db.mapper;

import java.time.LocalDateTime;

import org.col.api.TestEntityGenerator;
import org.col.api.model.DatasetImport;
import org.col.api.model.Name;
import org.col.api.model.Taxon;
import org.col.api.vocab.ImportState;
import org.col.db.PgSetupRule;
import org.col.db.dao.DatasetImportDao;
import org.gbif.nameparser.api.Rank;
import org.junit.ClassRule;
import org.junit.Rule;

import static org.col.api.vocab.Datasets.DRAFT_CAT;

/**
 * A reusable base class for all mybatis mapper tests that takes care of postgres & mybatis.
 * It offers a mapper to test in the implementing subclass.
 */
public abstract class MapperTestBase<T> {


  private final Class<T> mapperClazz;

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public InitMybatisRule initMybatisRule = InitMybatisRule.apple();

  public MapperTestBase(Class<T> mapperClazz) {
    this.mapperClazz = mapperClazz;
  }

  public T mapper() {
    return initMybatisRule.getMapper(mapperClazz);
  }

  public <X> X mapper(Class<X> clazz) {
    return initMybatisRule.getMapper(clazz);
  }

  public void commit() {
    initMybatisRule.commit();
  }
  
  protected void generateDatasetImport(int datasetKey) {
    DatasetImportMapper dim = mapper(DatasetImportMapper.class);

    DatasetImport d = new DatasetImport();
    d.setDatasetKey(datasetKey);
    d.setState(ImportState.FINISHED);
    d.setStarted(LocalDateTime.now());
    d.setDownload(LocalDateTime.now());
    d.setFinished(LocalDateTime.now());
    dim.create(d);

    DatasetImportDao dao = new DatasetImportDao(null);
    dao.updateMetrics(dim, d);
    dim.update(d);
    commit();
  }
  
  protected void partition(int datasetKey) {
    final DatasetPartitionMapper pm = mapper(DatasetPartitionMapper.class);
    pm.delete(datasetKey);
    pm.create(datasetKey);
    pm.attach(datasetKey);
    commit();
  }
  
  
  protected void populateDraftTree() {
    partition(DRAFT_CAT);
    
    NameMapper nm = mapper(NameMapper.class);
  
    Name n1 = draftName(nm,"n1", "Animalia", Rank.KINGDOM);
    Name n2 = draftName(nm,"n2", "Arthropoda", Rank.KINGDOM);
    Name n3 = draftName(nm,"n3", "Insecta", Rank.CLASS);
    Name n4 = draftName(nm,"n4", "Coleoptera", Rank.ORDER);
    Name n5 = draftName(nm,"n5", "Lepidoptera", Rank.ORDER);
    
    TaxonMapper tm = mapper(TaxonMapper.class);
    Taxon t1 = draftTaxon(tm,"t1", n1, null);
    Taxon t2 = draftTaxon(tm,"t2", n2, t1);
    Taxon t3 = draftTaxon(tm,"t3", n3, t2);
    Taxon t4 = draftTaxon(tm,"t4", n4, t3);
    Taxon t5 = draftTaxon(tm,"t5", n5, t3);
    
    commit();
  }
  
  private static Name draftName(NameMapper nm, String id, String name, Rank rank) {
    Name n = TestEntityGenerator.newName(DRAFT_CAT,id, name, rank);
    nm.create(n);
    return n;
  }
  
  private static Taxon draftTaxon(TaxonMapper tm, String id, Name n, Taxon parent) {
    Taxon t = TestEntityGenerator.newTaxon(DRAFT_CAT, id);
    t.setName(n);
    if (parent == null) {
      t.setParentId(null);
    } else {
      t.setParentId(parent.getId());
    }
    tm.create(t);
    return t;
  }
}