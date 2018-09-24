package org.col.db.mapper;

import java.time.LocalDateTime;

import org.col.api.model.DatasetImport;
import org.col.api.vocab.ImportState;
import org.col.db.PgSetupRule;
import org.col.db.dao.DatasetImportDao;
import org.junit.ClassRule;
import org.junit.Rule;

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

  public void generateDatasetImport(int datasetKey) {
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
}