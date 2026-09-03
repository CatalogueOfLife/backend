package life.catalogue.api.search;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.jackson.SerdeTestBase;
import life.catalogue.api.vocab.NomStatus;
import life.catalogue.api.vocab.TaxonomicStatus;

import org.gbif.nameparser.api.Rank;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

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
    s.setContent(new HashSet<>(Arrays.asList(NameUsageRequest.SearchContent.AUTHORSHIP)));
    s.setSortBy(NameUsageRequest.SortBy.TAXONOMIC);
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

  @Test(expected = IllegalArgumentException.class)
  public void badBoolean() {
    NameUsageSearchRequest r = new NameUsageSearchRequest();
    r.addFilter(NameUsageSearchParameter.EXTINCT, "extinct");
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

    r.addFilter(NameUsageSearchParameter.EXTINCT, true);
    assertTrue(r.getFilterValue(NameUsageSearchParameter.EXTINCT));
    r.addFilter(NameUsageSearchParameter.EXTINCT, "1");
    assertTrue(r.getFilterValue(NameUsageSearchParameter.EXTINCT));
  }

  @Test
  public void jsonConstructor() {
    // all nulls
    NameUsageSearchRequest r = new NameUsageSearchRequest(null,null,null,null,null,null,null,null,null,false,null,null,null);
    // empty collections
    r = new NameUsageSearchRequest(Map.of(), Set.of(), null, null, null, null, Set.of(),null,null,false,null,null,null);
  }

  @Test
  public void copy01() {
    NameUsageSearchRequest r = new NameUsageSearchRequest();
    r.addFilter(NameUsageSearchParameter.DATASET_KEY, "123");
    r.addFilter(NameUsageSearchParameter.DATASET_KEY, 1234);
    assertEquals(r, r.copy());

    r.setSingleContent(null);
    assertEquals(r, r.copy());

    r.setContent(Sets.newHashSet(NameUsageRequest.SearchContent.AUTHORSHIP));
    assertEquals(r, r.copy());

    r.setContent(Sets.newHashSet());
    r.copy();
  }

  @Test
  public void copy02() {
    NameUsageSearchRequest r0 = new NameUsageSearchRequest();
    NameUsageSearchRequest r1 = r0.copy();
    assertFalse(r1.hasFilters());
    assertTrue(r1.getFilters().isEmpty());
    assertTrue(r1.getFacets().isEmpty());
    assertEquals(NameUsageSearchRequest.DEFAULT_CONTENT, r1.getContent());
  }

  /**
   * Backwards compatibility: the legacy SearchType values WHOLE_WORDS and PREFIX must still
   * deserialize to STANDARD (via @JsonAlias) so portal-components and other external clients
   * that still send these values keep working.
   */
  @Test
  public void searchTypeBackwardsCompat() throws Exception {
    for (String legacy : new String[]{"WHOLE_WORDS", "PREFIX", "whole_words", "prefix"}) {
      NameUsageRequest.SearchType st = ApiModule.MAPPER.readValue('"' + legacy + '"', NameUsageRequest.SearchType.class);
      assertEquals("Legacy '" + legacy + "' should resolve to STANDARD",
          NameUsageRequest.SearchType.STANDARD, st);
    }
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

  /**
   * The ChecklistBank search page hands its entire URL query string to the search and to the
   * search download (POST /dataset/{key}/export/search), so every non filter request parameter
   * it uses must be ignored here instead of blowing up as an unknown filter.
   */
  @Test
  public void addFiltersFromQueryParams() {
    MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
    // real filters, as the UI spells them
    params.add("rank", "SPECIES");
    params.add("status", "ACCEPTED");
    params.add("status", "PROVISIONALLY_ACCEPTED");
    params.add("sectorDatasetKey", "2037");
    // request parameters that are not filters and must be skipped
    params.add("q", "Abies");
    params.add("content", "SCIENTIFIC_NAME");
    params.add("facet", "rank");
    params.add("sortBy", "relevance");
    params.add("reverse", "false");
    params.add("type", "FUZZY");
    params.add("limit", "50");
    params.add("offset", "0");

    NameUsageSearchRequest req = new NameUsageSearchRequest();
    req.addFilters(params);

    assertEquals(3, req.getFilters().size());
    assertEquals(Set.of(Rank.SPECIES), req.getFilterValues(NameUsageSearchParameter.RANK));
    assertEquals(Set.of(TaxonomicStatus.ACCEPTED, TaxonomicStatus.PROVISIONALLY_ACCEPTED),
      req.getFilterValues(NameUsageSearchParameter.STATUS));
    assertEquals(Set.of(2037), req.getFilterValues(NameUsageSearchParameter.SECTOR_DATASET_KEY));
  }

  @Test(expected = IllegalArgumentException.class)
  public void addFiltersRejectsUnknownParam() {
    MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
    params.add("notAFilter", "x");
    new NameUsageSearchRequest().addFilters(params);
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
