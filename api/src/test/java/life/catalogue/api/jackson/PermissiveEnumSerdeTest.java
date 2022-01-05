package life.catalogue.api.jackson;

import life.catalogue.api.search.NameUsageSearchParameter;
import life.catalogue.api.vocab.NomStatus;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PermissiveEnumSerdeTest extends SerdeMapEnumKeyTestBase<NomStatus>{

  public PermissiveEnumSerdeTest() {
    super(NomStatus.class);
  }

  @Test
  public void enumValueName() {
    assertEquals("dataset key", PermissiveEnumSerde.enumValueName(NameUsageSearchParameter.DATASET_KEY));
    assertEquals("extinct", PermissiveEnumSerde.enumValueName(NameUsageSearchParameter.EXTINCT));
  }

}