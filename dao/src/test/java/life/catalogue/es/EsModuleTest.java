package life.catalogue.es;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.search.NameUsageWrapper;
import org.junit.Test;

import static org.junit.Assert.*;

public class EsModuleTest {

  @Test
  public void write() throws Exception {
    NameUsageWrapper nuw = TestEntityGenerator.newNameUsageTaxonWrapper();
    System.out.println( EsModule.write(nuw) );
  }
}