package life.catalogue.api.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ProjectContributionTest {

  @Test
  public void testAdd() {
    ProjectContribution contrib = new ProjectContribution();
    contrib.add(DatasetTest.generateTestDataset());
    contrib.add(DatasetTest.generateTestDataset());

    assertEquals(1, contrib.getContributor().size());
    assertEquals(1, contrib.getOrganisations().size());

    Dataset d = DatasetTest.generateTestDataset();
    d.getAuthors().add(Person.parse("Mama"));
    d.getEditors().add(Person.parse("Mama Joe"));
    d.getOrganisations().add(Organisation.parse("Mama-Joe"));
    contrib.add(d);

    assertEquals(3, contrib.getContributor().size());
    assertEquals(2, contrib.getOrganisations().size());
  }
}