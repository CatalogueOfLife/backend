package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.junit.TestDataRule;

import java.util.List;

import org.junit.Test;

import static life.catalogue.api.TestEntityGenerator.NAME4;
import static org.junit.Assert.assertEquals;


public class TaxonMapperTreeTest extends MapperTestBase<TaxonMapper> {
  
  public TaxonMapperTreeTest() {
    super(TaxonMapper.class, TestDataRule.tree());
  }
  
  @Test
  public void classificationSimple() throws Exception {
    List<?> cl = mapper().classificationSimple(DSID.of(NAME4.getDatasetKey(), "t15"));
    assertEquals(7, cl.size());
  }
  
}
