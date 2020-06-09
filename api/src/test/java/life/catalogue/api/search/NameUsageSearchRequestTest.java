package life.catalogue.api.search;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.jackson.SerdeTestBase;
import life.catalogue.api.search.NameUsageSearchRequest.SearchContent;
import life.catalogue.api.vocab.NomStatus;
import org.junit.Test;

import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;

import static org.junit.Assert.*;

public class NameUsageSearchRequestTest extends SerdeTestBase<NameUsageSearchRequest> {

  public NameUsageSearchRequestTest() {
    super(NameUsageSearchRequest.class);
  }

  @Test
  public void equals() throws Exception {
    assertEquals(genTestValue(), genTestValue());
  }

  @Override
  public NameUsageSearchRequest genTestValue() throws Exception {
    NameUsageSearchRequest s = new NameUsageSearchRequest();
    s.setQ("Abies");
    s.setContent(new HashSet<>(Arrays.asList(NameUsageSearchRequest.SearchContent.AUTHORSHIP)));
    s.setSortBy(NameUsageSearchRequest.SortBy.TAXONOMIC);
    s.addFilter(NameUsageSearchParameter.NOM_STATUS, NomStatus.MANUSCRIPT);
    s.addFilter(NameUsageSearchParameter.NOM_STATUS, NomStatus.CHRESONYM);
    return s;
  }

  @Test(expected = IllegalArgumentException.class)
  public void badInt() {
    NameUsageSearchRequest r = new NameUsageSearchRequest();
    r.addFilter(NameUsageSearchParameter.DATASET_KEY, "fgh");
  }

  @Test(expected = IllegalArgumentException.class)
  public void badEnum() {
    NameUsageSearchRequest r = new NameUsageSearchRequest();
    r.addFilter(NameUsageSearchParameter.RANK, "spezi");
  }

  @Test
  public void addFilterGood() {
    NameUsageSearchRequest r = new NameUsageSearchRequest();
    r.addFilter(NameUsageSearchParameter.DATASET_KEY, "123");
    r.addFilter(NameUsageSearchParameter.DATASET_KEY, 1234);
    assertEquals(ImmutableSet.of(123, 1234), r.getFilterValues(NameUsageSearchParameter.DATASET_KEY));
    r.addFilter(NameUsageSearchParameter.DATASET_KEY, Lists.newArrayList(1234, 12, 13, 14));
    assertEquals(ImmutableSet.of(123, 1234, 1234, 12, 13, 14), r.getFilterValues(NameUsageSearchParameter.DATASET_KEY));

    r.addFilter(NameUsageSearchParameter.DATASET_KEY, Lists.newArrayList("1", "2"));
    assertEquals(ImmutableSet.of(123, 1234, 1234, 12, 13, 14, 1, 2), r.getFilterValues(NameUsageSearchParameter.DATASET_KEY));
  }

  @Test
  public void copy01() {
    NameUsageSearchRequest r = new NameUsageSearchRequest();
    r.addFilter(NameUsageSearchParameter.DATASET_KEY, "123");
    r.addFilter(NameUsageSearchParameter.DATASET_KEY, 1234);
    assertEquals(r, r.copy());

    r.setContent(null);
    assertEquals(r, r.copy());

    r.setContent(Sets.newHashSet(NameUsageSearchRequest.SearchContent.AUTHORSHIP));
    assertEquals(r, r.copy());

    r.setContent(Sets.newHashSet());
    r.copy();
  }

  @Test // Tests #510 (bad behaviour if filters/facets/content is empty).
  public void copy02() {
    NameUsageSearchRequest r0 = new NameUsageSearchRequest();
    NameUsageSearchRequest r1 = r0.copy();
    assertFalse(r1.hasFilters());
    assertTrue(r1.getFacets().isEmpty());
    assertEquals(EnumSet.allOf(SearchContent.class),r1.getContent());  
  }

  @Test
  public void restrictFilterSize() {
    ValidatorFactory vf = Validation.buildDefaultValidatorFactory();
    Validator validator = vf.getValidator();

    NameUsageSearchRequest req = new NameUsageSearchRequest();
    req.addFacet(NameUsageSearchParameter.DATASET_KEY);
    for (int x = 0; x<999; x++) {
      req.addFilter(NameUsageSearchParameter.USAGE_ID, "id"+x);
    }
    assertEquals(999, req.getFilterValues(NameUsageSearchParameter.USAGE_ID).size());
    assertTrue(validator.validate(req).isEmpty());

    req.addFilter(NameUsageSearchParameter.USAGE_ID, "id1000");
    assertTrue(validator.validate(req).isEmpty());

    req.addFilter(NameUsageSearchParameter.DATASET_KEY, 1000);
    req.addFilter(NameUsageSearchParameter.DATASET_KEY, 1001);
    assertTrue(validator.validate(req).isEmpty());

    req.addFilter(NameUsageSearchParameter.USAGE_ID, "id1001");
    assertFalse(validator.validate(req).isEmpty());
  }

  protected void debug(String json, Wrapper<NameUsageSearchRequest> wrapper, Wrapper<NameUsageSearchRequest> wrapper2) {
    try {
      System.out.println(ApiModule.MAPPER.writeValueAsString(wrapper.value));
      System.out.println(ApiModule.MAPPER.writeValueAsString(wrapper2.value));
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
  }
}
