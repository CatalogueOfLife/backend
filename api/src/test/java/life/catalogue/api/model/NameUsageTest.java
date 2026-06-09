package life.catalogue.api.model;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.vocab.TaxonomicStatus;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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

  @Test
  public void statusOrder() {
    Name n = TestEntityGenerator.newName(23, "1234", "Abies");
    // accepted taxa -> 0
    assertEquals(0, NameUsage.create(TaxonomicStatus.ACCEPTED, n).getStatusOrder());
    assertEquals(0, NameUsage.create(TaxonomicStatus.PROVISIONALLY_ACCEPTED, n).getStatusOrder());
    // any synonym -> 1, regardless of the specific synonym status
    assertEquals(1, NameUsage.create(TaxonomicStatus.SYNONYM, n).getStatusOrder());
    assertEquals(1, NameUsage.create(TaxonomicStatus.AMBIGUOUS_SYNONYM, n).getStatusOrder());
    assertEquals(1, NameUsage.create(TaxonomicStatus.MISAPPLIED, n).getStatusOrder());
    // bare name -> 2
    assertEquals(2, NameUsage.create(TaxonomicStatus.BARE_NAME, n).getStatusOrder());
    // an unset status defaults to accepted (0)
    assertEquals(0, new Taxon().getStatusOrder());
  }
}