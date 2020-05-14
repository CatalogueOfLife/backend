package life.catalogue;

import life.catalogue.parser.NomenOntology;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NomenOntologyTest {

  @Test
  public void test(){
    NomenOntology nomen = new NomenOntology();
    int countStatus = 0;
    int countCode = 0;
    for (NomenOntology.Nomen n : nomen.list()) {
      if (n.status != null) {
        countStatus++;
      }
      if (n.code != null) {
        countCode++;
      }
    }
    assertEquals(253, nomen.size());
    assertEquals(nomen.size(), nomen.list().size());
    assertEquals(124, countCode);
    assertEquals(115, countStatus);
  }
}