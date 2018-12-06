package org.col.api.search;

import java.util.Arrays;
import java.util.HashSet;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.col.api.jackson.ApiModule;
import org.col.api.jackson.SerdeTestBase;
import org.col.api.vocab.NomStatus;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NameSearchRequestTest extends SerdeTestBase<NameSearchRequest> {
  
  public NameSearchRequestTest() {
    super(NameSearchRequest.class);
  }
  
  @Override
  public NameSearchRequest genTestValue() throws Exception {
    NameSearchRequest s = new NameSearchRequest();
    s.setQ("Abies");
    s.setContent(new HashSet<>(Arrays.asList(NameSearchRequest.SearchContent.AUTHORSHIP)));
    s.setSortBy(NameSearchRequest.SortBy.NATIVE);
    s.addFilter(NameSearchParameter.NOM_STATUS, NomStatus.MANUSCRIPT);
    s.addFilter(NameSearchParameter.NOM_STATUS, NomStatus.CHRESONYM);
    return s;
  }

  @Test(expected = IllegalArgumentException.class)
  public void badInt() {
    NameSearchRequest r = new NameSearchRequest();
    r.addFilter(NameSearchParameter.DATASET_KEY, "fgh");
  }

  @Test(expected = IllegalArgumentException.class)
  public void badEnum() {
    NameSearchRequest r = new NameSearchRequest();
    r.addFilter(NameSearchParameter.RANK, "spezi");
  }

  @Test
  public void addFilterGood() {
    NameSearchRequest r = new NameSearchRequest();
    r.addFilter(NameSearchParameter.DATASET_KEY, "123");
    r.addFilter(NameSearchParameter.DATASET_KEY, 1234);
    assertEquals(ImmutableList.of(123, 1234), r.getFilterValue(NameSearchParameter.DATASET_KEY));
    r.addFilter(NameSearchParameter.DATASET_KEY, Lists.newArrayList(1234, 12, 13, 14));
    assertEquals(ImmutableList.of(123, 1234, 1234, 12, 13, 14), r.getFilterValue(NameSearchParameter.DATASET_KEY));

    r.addFilter(NameSearchParameter.DATASET_KEY, Lists.newArrayList("1", "2"));
    assertEquals(ImmutableList.of(123, 1234, 1234, 12, 13, 14, 1, 2), r.getFilterValue(NameSearchParameter.DATASET_KEY));
  }

  @Ignore // Filter params now get converted to appropriate type before they enter the multivalued map.
  @Test
  public void allFilterParams() {
    NameSearchRequest r = new NameSearchRequest();

    for (NameSearchParameter p : NameSearchParameter.values()) {
      String val = testVal(p);
      r.addFilter(p, val);
      r.addFilter(p, Lists.newArrayList(val, val));
      assertEquals(ImmutableList.of(val, val, val), r.getFilterValue(p));
    }
    assertEquals(NameSearchParameter.values().length, r.getFilters().size());
  }

  private String testVal(NameSearchParameter p) {
    if (String.class.isAssignableFrom(p.type())) {
      return "E45 franz";
    } else if (Integer.class.isAssignableFrom(p.type())) {
      return "23456";
    } else if (p.type().isEnum()) {
      Enum[] values = ((Class<? extends Enum<?>>) p.type()).getEnumConstants();
      return String.valueOf(values[0].ordinal());
    } else {
      throw new IllegalStateException(NameSearchParameter.class.getSimpleName() + " missing converter for data type " + p.type());
    }
  }
  
  
  protected void debug(String json, Wrapper<NameSearchRequest> wrapper, Wrapper<NameSearchRequest> wrapper2) {
    try {
      System.out.println(ApiModule.MAPPER.writeValueAsString(wrapper.value));
      System.out.println(ApiModule.MAPPER.writeValueAsString(wrapper2.value));
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
  }
}
