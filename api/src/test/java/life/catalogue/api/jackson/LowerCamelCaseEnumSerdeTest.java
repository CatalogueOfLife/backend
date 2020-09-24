package life.catalogue.api.jackson;

import life.catalogue.api.search.NameUsageSearchParameter;
import org.junit.Test;

import static org.junit.Assert.*;

public class LowerCamelCaseEnumSerdeTest extends SerdeMapEnumKeyTestBase<NameUsageSearchParameter>{

  public LowerCamelCaseEnumSerdeTest() {
    super(NameUsageSearchParameter.class);
  }

  @Test
  public void enumValueName() {
    assertEquals("datasetKey", LowerCamelCaseEnumSerde.lowerCamelCase(NameUsageSearchParameter.DATASET_KEY));
    assertEquals("extinct", LowerCamelCaseEnumSerde.lowerCamelCase(NameUsageSearchParameter.EXTINCT));
    assertEquals("nameIndexId", LowerCamelCaseEnumSerde.lowerCamelCase(NameUsageSearchParameter.NAME_INDEX_ID));
  }

}