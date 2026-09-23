package life.catalogue.matching.person.harvest;

import life.catalogue.api.vocab.TaxGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.Test;

import static org.junit.Assert.*;

public class GroupsTest {

  @Test
  public void ipniGroups() {
    List<String> unmapped = new ArrayList<>();
    assertEquals(Set.of(TaxGroup.Fungi, TaxGroup.Angiosperms, TaxGroup.Gymnosperms, TaxGroup.Algae, TaxGroup.Pteridophytes),
      Groups.ipni("Mycology, Spermatophytes, Algae, Pteridophytes", unmapped::add));
    // fossils and pre-Linnaean works are no group of organisms and are dropped without a word
    assertEquals(Set.of(TaxGroup.Bryophytes), Groups.ipni("Bryophytes, Fossils, Pre-Linnaean", unmapped::add));
    assertEquals(Set.of(), Groups.ipni("", unmapped::add));
    assertEquals(Set.of(), Groups.ipni("Lichens", unmapped::add));
    assertEquals(List.of("Lichens"), unmapped);
  }

  @Test
  public void fieldsOfWork() {
    assertEquals(Set.of(TaxGroup.Plants, TaxGroup.Fungi), Groups.field("botany"));
    assertEquals(Set.of(TaxGroup.Molluscs), Groups.field("Malacology"));
    assertEquals(Set.of(TaxGroup.Coleoptera), Groups.field("coleopterology"));
    assertEquals(Set.of(), Groups.field("politics"));
  }
}
