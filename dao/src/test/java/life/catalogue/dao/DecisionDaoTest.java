package life.catalogue.dao;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.exception.NotUniqueException;
import life.catalogue.api.model.EditorialDecision;
import life.catalogue.db.mapper.DecisionMapperTest;
import life.catalogue.es.indexing.NameUsageIndexService;

import org.junit.Before;
import org.junit.Test;

import static life.catalogue.api.TestEntityGenerator.DATASET11;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DecisionDaoTest extends DaoTestBase {
  static int user = TestEntityGenerator.USER_EDITOR.getKey();

  DecisionDao dao;

  @Before
  public void init(){
    dao = new DecisionDao(factory(), NameUsageIndexService.passThru(), validator);
  }

  /**
   * Creating a second decision for the same subject violates the unique constraint
   * (dataset_key, subject_dataset_key, subject_id) and must surface as a NotUniqueException,
   * not as a raw MyBatis PersistenceException.
   * https://github.com/CatalogueOfLife/backend/issues/1526
   */
  @Test
  public void duplicateSubject() {
    EditorialDecision d1 = DecisionMapperTest.create(DATASET11.getKey());
    d1.getSubject().setId("foo");
    dao.create(d1, user);

    EditorialDecision d2 = DecisionMapperTest.create(DATASET11.getKey());
    d2.getSubject().setId("foo");
    try {
      dao.create(d2, user); // duplicate subject
      fail("Expected a NotUniqueException");
    } catch (NotUniqueException e) {
      // the message must be human readable, not the raw MyBatis/PersistenceException dump
      assertFalse(e.getMessage(), e.getMessage().contains("PersistenceException"));
      assertFalse(e.getMessage(), e.getMessage().contains("###"));
      assertTrue(e.getMessage(), e.getMessage().contains("already exists"));
    }
  }
}
