package org.col.api.search;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import org.col.api.jackson.ApiModule;
import org.col.api.jackson.SerdeTestBase;
import org.col.api.search.NameSearchRequest.SearchContent;
import org.col.api.vocab.NomStatus;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
    assertEquals(ImmutableList.of(123, 1234), r.getFilterValues(NameSearchParameter.DATASET_KEY));
    r.addFilter(NameSearchParameter.DATASET_KEY, Lists.newArrayList(1234, 12, 13, 14));
    assertEquals(ImmutableList.of(123, 1234, 1234, 12, 13, 14), r.getFilterValues(NameSearchParameter.DATASET_KEY));

    r.addFilter(NameSearchParameter.DATASET_KEY, Lists.newArrayList("1", "2"));
    assertEquals(ImmutableList.of(123, 1234, 1234, 12, 13, 14, 1, 2), r.getFilterValues(NameSearchParameter.DATASET_KEY));
  }

  @Test
  public void copy01() {
    NameSearchRequest r = new NameSearchRequest();
    r.addFilter(NameSearchParameter.DATASET_KEY, "123");
    r.addFilter(NameSearchParameter.DATASET_KEY, 1234);
    assertEquals(r, r.copy());

    r.setContent(null);
    assertEquals(r, r.copy());

    r.setContent(Sets.newHashSet(NameSearchRequest.SearchContent.AUTHORSHIP));
    assertEquals(r, r.copy());

    r.setContent(Sets.newHashSet());
    r.copy();
  }

  @Test // Tests #510 (bad behaviour if filters/facets/content is empty).
  public void copy02() {
    NameSearchRequest r0 = new NameSearchRequest();
    NameSearchRequest r1 = r0.copy();  
    assertFalse(r1.hasFilters());
    assertTrue(r1.getFacets().isEmpty());
    assertEquals(EnumSet.allOf(SearchContent.class),r1.getContent());  
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
