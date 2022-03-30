package life.catalogue.api.model;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.jackson.SerdeTestBase;
import life.catalogue.api.vocab.TaxonomicStatus;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 */
public class SynonymyTest extends SerdeTestBase<Synonymy> {
  
  public SynonymyTest() {
    super(Synonymy.class);
  }
  
  @Override
  public Synonymy genTestValue() throws Exception {
    Synonymy s = TestEntityGenerator.newSynonymy();
    return s;
  }
  
  @Test
  public void isEmpty() throws Exception {
    Synonymy s = new Synonymy();
    assertTrue(s.isEmpty());
    
    s = TestEntityGenerator.newSynonymy();
    assertFalse(s.isEmpty());
  }
  
  @Test
  public void size() throws Exception {
    Synonymy s = new Synonymy();
    s.getHeterotypic().addAll(synonyms(3, TaxonomicStatus.SYNONYM));
    s.getHeterotypic().addAll(synonyms(1, TaxonomicStatus.AMBIGUOUS_SYNONYM));
    s.getMisapplied().addAll(synonyms(1, TaxonomicStatus.MISAPPLIED));
    s.getHomotypic().addAll(synonyms(8, TaxonomicStatus.SYNONYM));
    assertEquals(13, s.size());
  }

  List<Synonym> synonyms(int num, TaxonomicStatus status) {
    return TestEntityGenerator.newNames(num).stream().map(n -> {
      var s = new Synonym();
      s.setName(n);
      s.setStatus(status);
      return s;
    }).collect(Collectors.toList());
  }
  
}