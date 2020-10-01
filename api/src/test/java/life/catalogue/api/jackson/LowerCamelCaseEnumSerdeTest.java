package life.catalogue.api.jackson;

import life.catalogue.api.search.NameUsageSearchParameter;

public class LowerCamelCaseEnumSerdeTest extends SerdeMapEnumKeyTestBase<NameUsageSearchParameter>{

  public LowerCamelCaseEnumSerdeTest() {
    super(NameUsageSearchParameter.class);
  }

}