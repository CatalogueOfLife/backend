package life.catalogue.api.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ProjectContributionTest {

  @Test
  public void testAdd() {
    ProjectContribution contrib = new ProjectContribution();
    contrib.add(DatasetTest.generateTestDataset());
    contrib.add(DatasetTest.generateTestDataset());

    assertEquals(0, contrib.getContributor().size());
    assertEquals(1, contrib.getOrganisations().size());

    Dataset d = DatasetTest.generateTestDataset();
    d.getAuthors().add(Person.parse("Mama"));
    d.getEditors().add(Person.parse("Mama Joe"));
    d.getOrganisations().add(Organisation.parse("Mama-Joe"));
    contrib.add(d);

    assertEquals(2, contrib.getContributor().size());
    assertEquals(2, contrib.getOrganisations().size());

    // ignore the empty persons and orgs
    d = DatasetTest.generateTestDataset();
    d.getAuthors().add(new Person(null, null, "null@null.io", null));
    d.getEditors().add(new Person(null, null, "null@null.io", null));
    d.getOrganisations().add(new Organisation(null, null, null, null, null));
    contrib.add(d);

    assertEquals(2, contrib.getContributor().size());
    assertEquals(2, contrib.getOrganisations().size());
  }
}