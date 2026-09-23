package life.catalogue.matching.person;

import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Guards the registry that ships with the code: every reference resolves, ids are unique and consistent, every
 * person has a name.
 */
public class PersonRegistryFilesTest {

  @Test
  public void committedRegistryIsConsistent() throws Exception {
    assertEquals(List.of(), new PersonRegistry(PersonFiles.readResources()).problems());
  }
}
