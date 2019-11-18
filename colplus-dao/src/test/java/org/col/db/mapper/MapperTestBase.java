package org.col.db.mapper;

import org.apache.ibatis.session.SqlSession;
import org.col.api.model.Name;
import org.col.api.model.Synonym;
import org.col.api.model.Taxon;
import org.col.dao.DatasetImportDao;
import org.col.dao.TreeRepoRule;
import org.col.db.PgSetupRule;
import org.javers.core.Javers;
import org.javers.core.JaversBuilder;
import org.javers.core.diff.Diff;
import org.junit.ClassRule;
import org.junit.Rule;

/**
 * A reusable base class for all mybatis mapper tests that takes care of postgres & mybatis.
 * It offers a mapper to test in the implementing subclass.
 */
public abstract class MapperTestBase<M> {
  
  private final Class<M> mapperClazz;
  
  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();
  
  @Rule
  public final TestDataRule testDataRule;
  
  @Rule
  public final TreeRepoRule treeRepoRule = new TreeRepoRule();
  
  public MapperTestBase() {
    this(null, TestDataRule.empty());
  }

  public MapperTestBase(Class<M> mapperClazz) {
    this(mapperClazz, TestDataRule.apple());
  }
  
  public MapperTestBase(Class<M> mapperClazz, TestDataRule testDataRule) {
    this.mapperClazz = mapperClazz;
    this.testDataRule = testDataRule;
  }
  
  public M mapper() {
    return testDataRule.getMapper(mapperClazz);
  }
  
  public <X> X mapper(Class<X> clazz) {
    return testDataRule.getMapper(clazz);
  }
  
  public SqlSession session() {
    return testDataRule.getSqlSession();
  }

  public void commit() {
    testDataRule.commit();
  }
  
  protected void generateDatasetImport(int datasetKey) {
    commit();
    DatasetImportDao dao = new DatasetImportDao(PgSetupRule.getSqlSessionFactory(), treeRepoRule.getRepo());
    dao.createSuccess(datasetKey);
    commit();
  }
  
  void insertName(Name n) {
    mapper(NameMapper.class).create(n);
  }

  void insertTaxon(Taxon t) {
    mapper(NameMapper.class).create(t.getName());
    mapper(TaxonMapper.class).create(t);
  }
  
  void insertSynonym(Synonym s) {
    mapper(NameMapper.class).create(s.getName());
    mapper(SynonymMapper.class).create(s);
  }
  
  protected void printDiff(Object o1, Object o2) {
    Javers javers = JaversBuilder.javers().build();
    Diff diff = javers.compare(o1, o2);
    System.out.println(diff);
  }
}