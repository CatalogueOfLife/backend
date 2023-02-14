package life.catalogue.api.model;

import life.catalogue.api.TestEntityGenerator;

import life.catalogue.api.vocab.TaxonomicStatus;

import org.junit.Test;

import static org.junit.Assert.*;

public class NameUsageTest {

  @Test
  public void create() {
    Name n = TestEntityGenerator.newName(23,"1234", "Abies");

    var u = NameUsage.create(TaxonomicStatus.SYNONYM, n);
    assertTrue(u instanceof Synonym);

    u = NameUsage.create(TaxonomicStatus.PROVISIONALLY_ACCEPTED, n);
    assertTrue(u instanceof Taxon);
    assertEquals(TaxonomicStatus.PROVISIONALLY_ACCEPTED, u.getStatus());

    u = NameUsage.create(TaxonomicStatus.BARE_NAME, n);
    assertTrue(u instanceof BareName);
    assertEquals(TaxonomicStatus.BARE_NAME, u.getStatus());

    u = NameUsage.create(null, n);
    assertTrue(u instanceof Taxon);
    assertEquals(TaxonomicStatus.ACCEPTED, u.getStatus());
  }
}