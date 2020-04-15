package life.catalogue.db.mapper;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetScopedEntity;
import life.catalogue.api.model.Page;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.DatasetType;
import life.catalogue.api.vocab.Users;
import life.catalogue.dao.Partitioner;
import life.catalogue.db.CRUD;
import life.catalogue.db.DatasetPageable;
import life.catalogue.db.PgSetupRule;
import org.checkerframework.checker.units.qual.K;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 *
 */
abstract class CRUDDatasetScopedStringTestBase<T extends DatasetScopedEntity<String>, M extends CRUD<DSID<String>, T> & DatasetPageable<T>>
    extends CRUDPageableTestBase<String, T, M> {

  public CRUDDatasetScopedStringTestBase(Class<M> mapperClazz) {
    super(mapperClazz);
  }

  @Test
  public void getEmpty() throws Exception {
    assertNull(mapper().get(DSID.key(datasetKey, "")));
  }
  
  T createTestEntityIncId(int datasetKey) {
    T ent = createTestEntity(datasetKey);
    ent.setId(String.valueOf(TestEntityGenerator.ID_GEN.incrementAndGet()));
    return ent;
  }
  
}
