package life.catalogue.parser;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NomenOntologyTest {

  @Test
  public void test(){
    NomenOntology nomen = new NomenOntology();
    int countStatus = 0;
    int countCode = 0;
    int countRel = 0;
    for (NomenOntology.Nomen n : nomen.list()) {
      if (n.status != null) {
        countStatus++;
      }
      if (n.code != null) {
        countCode++;
      }
      if (n.nomRelType != null) {
        countRel++;
      }

    }
    assertEquals(124, countCode);
    assertEquals(115, countStatus);
    assertEquals(13, countRel);
    assertEquals(363, nomen.size());
    assertEquals(nomen.size(), nomen.list().size());
  }

}