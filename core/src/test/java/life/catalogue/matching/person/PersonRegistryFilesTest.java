package life.catalogue.matching.person;

import life.catalogue.api.model.Person;

import org.gbif.nameparser.api.NomCode;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Guards the registry that ships with the code: every reference resolves, ids are unique and consistent, every
 * person has a name.
 */
public class PersonRegistryFilesTest {

  @Test
  public void committedFilesRead() throws Exception {
    assertNotNull(PersonFiles.readResources());
  }

  @Test
  public void committedRegistryIsConsistent() throws Exception {
    assertEquals(List.of(), new MemoryPersonStore(PersonFiles.readResources()).problems());
  }

  /** the relatives phase 3 is measured on resolve, each to one person, and G. B. Sowerby III knows his father */
  @Test
  public void committedRegistryKnowsTheRelatives() {
    var reg = MemoryPersonStore.resources();
    Person sowerby3 = one(reg.candidates("G.B. Sowerby III", NomCode.ZOOLOGICAL));
    assertEquals("wd:Q1216378", sowerby3.id());
    assertEquals(Set.of("wd:Q1223045"), reg.relatives(sowerby3).stream().map(Person::id).collect(Collectors.toSet()));
    assertEquals("wd:Q157501", one(reg.candidates("Hooker f.", NomCode.BOTANICAL)).id());
    assertEquals("wd:Q379601", one(reg.candidates("K.B. Presl", NomCode.BOTANICAL)).id());
    assertEquals("wd:Q379601", one(reg.candidates("C.Presl", NomCode.BOTANICAL)).id());
    assertEquals("Linné", reg.get("wd:Q1043").family());
  }

  private static Person one(Set<Person> persons) {
    assertEquals(persons.toString(), 1, persons.size());
    return persons.iterator().next();
  }
}
