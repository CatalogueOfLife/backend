package life.catalogue.es;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.search.NameUsageWrapper;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class EsModuleTest {

  @Test
  public void roundtrip() throws Exception {
    NameUsageWrapper nuw = TestEntityGenerator.newNameUsageTaxonWrapper();

    // we hide the mode
    nuw.getUsage().setSectorMode(null);
    
    String json = EsModule.write(nuw);
    System.out.println(json);

    assertFalse(json.contains("label"));
    assertFalse(json.contains("labelHtml"));

    NameUsageWrapper nuw2 = EsModule.readNameUsageWrapper(json);
    assertEquals(nuw, nuw2);
  }
}