package life.catalogue.api.model;

import org.junit.Test;

import java.util.List;

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
    d.setCreator(List.of(Person.parse("Mama")));
    d.setEditor(List.of(Person.parse("Mama Joe")));
    d.setOrganisations(List.of(Organisation.parse("Mama-Joe")));
    contrib.add(d);

    assertEquals(2, contrib.getContributor().size());
    assertEquals(2, contrib.getOrganisations().size());

    // ignore the empty persons and orgs
    d = DatasetTest.generateTestDataset();
    d.setCreator(List.of(new Person(null, null, "null@null.io", null)));
    d.setEditor(List.of(new Person(null, null, "null@null.io", null)));
    d.getOrganisations().add(new Organisation(null, null, null, null, null));
    contrib.add(d);

    assertEquals(2, contrib.getContributor().size());
    assertEquals(2, contrib.getOrganisations().size());
  }
}