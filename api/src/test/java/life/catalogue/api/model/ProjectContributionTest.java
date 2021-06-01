package life.catalogue.api.model;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class ProjectContributionTest {

  @Test
  public void testAdd() {
    ProjectContribution contrib = new ProjectContribution();
    contrib.add(DatasetTest.generateTestDataset());
    assertEquals(8, contrib.size());
    // same agents again
    contrib.add(DatasetTest.generateTestDataset());
    assertEquals(8, contrib.size());

    Dataset d = DatasetTest.generateTestDataset();
    d.setCreator(List.of(Agent.parse("Mama")));
    d.setEditor(List.of(Agent.parse("Mama Joe")));
    d.setDistributor(List.of(Agent.parse("Mama-Joe")));
    contrib.add(d);

    assertEquals(11, contrib.size());

    // ignore the empty persons and orgs
    d = DatasetTest.generateTestDataset();
    d.setCreator(List.of(new Agent(null, null, "null@null.io", null)));
    d.setEditor(List.of(new Agent(null, null, "null@null.io", null)));
    d.getDistributor().add(new Agent(null, null, null, null, null, null, null, null, null));
    contrib.add(d);

    assertEquals(11, contrib.size());
  }
}