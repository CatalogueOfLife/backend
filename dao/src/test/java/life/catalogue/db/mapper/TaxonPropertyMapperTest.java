package life.catalogue.db.mapper;

import life.catalogue.api.RandomUtils;
import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.TaxonProperty;

import java.util.ArrayList;
import java.util.List;

public class TaxonPropertyMapperTest extends TaxonExtensionMapperTest<TaxonProperty, TaxonPropertyMapper> {

  public TaxonPropertyMapperTest() {
    super(TaxonPropertyMapper.class);
  }

  @Override
  List<TaxonProperty> createTestEntities() {
    List<TaxonProperty> list = new ArrayList<>();
    for (String prop : List.of("size", "maximumAge", "diagnosis", "color", RandomUtils.randomUri().toString(), RandomUtils.randomUri().toString())) {
      var tp = new TaxonProperty();
      tp.setProperty(prop);
      tp.setValue(RandomUtils.randomLatinString(25));
      tp.setOrdinal(12);
      tp.setPage("123");
      tp.setRemarks(RandomUtils.randomLatinString(100));
      list.add(TestEntityGenerator.setUserDate(tp));
    }
    return list;
  }
}