package life.catalogue.es.nu.search;

import life.catalogue.api.model.EditorialDecision.Mode;
import life.catalogue.api.search.NameUsageSearchParameter;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.api.search.SimpleDecision;
import life.catalogue.es.EsReadTestBase;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class MinRankMaxRankTest extends EsReadTestBase {
  
  @BeforeClass
  public static void beforeClass() {
    destroyAndCreateIndex();
  }

  
  @Before
  public void before() {
    truncate();
  }
  
  @Test
  public void test1() {
    
  }



  public List<NameUsageWrapper> testData() {
    return null;
  }

}
