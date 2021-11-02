package life.catalogue.api.model;

import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ProjectContributionTest {

  @Test
  public void testAdd() {
    ProjectContribution contrib = new ProjectContribution();
    contrib.add(DatasetTest.generateTestDataset());
    assertEquals(7, contrib.size());
    // same agents again
    contrib.add(DatasetTest.generateTestDataset());
    assertEquals(7, contrib.size());

    Dataset d = DatasetTest.generateTestDataset();
    d.setCreator(List.of(Agent.parse("Mama")));
    d.setEditor(List.of(Agent.parse("Mama Joe")));
    d.setContributor(List.of(Agent.parse("Mama-Joe")));
    contrib.add(d);

    assertEquals(10, contrib.size());

    // ignore the empty persons and orgs
    d = DatasetTest.generateTestDataset();
    d.setCreator(List.of(Agent.person(null, null, "null@null.io")));
    d.setEditor(List.of(Agent.person(null, null, "null@null.io")));
    d.getContributor().add(new Agent(null, null));
    contrib.add(d);

    assertEquals(10, contrib.size());

    // Add same agent again with different source
    d = DatasetTest.generateTestDataset();
    d.setKey(101);
    d.setCreator(List.of(Agent.parse("Mama")));
    d.setEditor(List.of(Agent.parse("M. Joe")));
    contrib.add(d);

    assertEquals(10, contrib.size());
    for (ProjectContribution.Contributor c : contrib) {
      if (c.getName().equals("Joe M.")) {
        assertEquals(2, c.getSources().size());
      }
    }
  }
}