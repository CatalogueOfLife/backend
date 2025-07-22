package life.catalogue.matching;

import life.catalogue.matching.index.DatasetIndex;
import life.catalogue.matching.model.Dataset;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class IDMatchingTest {

  @Test
  public void testWithoutPrefix() {
    Dataset dataset = Dataset.builder()
      .clbKey(1)
      .prefix("urn:lsid:ipni.org:names:")
      .prefixMapping(List.of("urn:lsid:ipni.org:names:", "ipni:"))
      .removePrefixForMatching(true)
      .build();
    assertEquals(Optional.of("1"), DatasetIndex.extractKeyForSearch("ipni:1", dataset));
  }

  @Test
  public void testWithoutPrefix2() {
    Dataset dataset = Dataset.builder()
      .clbKey(1)
      .prefix("gbif:")
      .prefixMapping(List.of("gbif:"))
      .removePrefixForMatching(true)
      .build();
    assertEquals(Optional.of("1"), DatasetIndex.extractKeyForSearch("gbif:1", dataset));
  }

  @Test
  public void testWithPrefix() {
    Dataset dataset = Dataset.builder()
      .clbKey(1)
      .prefix("urn:lsid:marinespecies.org:taxname:")
      .prefixMapping(List.of("worms:"))
      .removePrefixForMatching(false)
      .build();
    assertEquals(Optional.of("urn:lsid:marinespecies.org:taxname:1"),
      DatasetIndex.extractKeyForSearch("worms:1", dataset)
    );
  }

  @Test
  public void testWithUnrecognisedPrefix() {
    Dataset dataset = Dataset.builder()
      .clbKey(1)
      .prefix("urn:lsid:marinespecies.org:taxname:")
      .prefixMapping(List.of("worms:"))
      .removePrefixForMatching(false)
      .build();
    assertEquals(Optional.empty(),
      DatasetIndex.extractKeyForSearch("nonsense:1", dataset)
    );
  }

  @Test
  public void testWithUnrecognisedPrefix2() {
    Dataset dataset = Dataset.builder()
      .clbKey(1)
      .prefix("urn:lsid:marinespecies.org:taxname:")
      .prefixMapping(List.of("worms:"))
      .removePrefixForMatching(false)
      .build();
    assertEquals(Optional.empty(),
      DatasetIndex.extractKeyForSearch("1", dataset)
    );
  }
}
