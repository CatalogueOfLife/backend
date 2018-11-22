package org.col.es.translate;

import java.util.EnumSet;

import com.fasterxml.jackson.core.JsonProcessingException;

import org.col.api.model.Page;
import org.col.api.search.NameSearchRequest;
import org.col.api.search.NameSearchRequest.SearchContent;
import org.col.api.vocab.Issue;
import org.col.es.EsModule;
import org.col.es.query.EsSearchRequest;
import org.gbif.nameparser.api.Rank;
import org.junit.Test;

import static org.col.api.search.NameSearchParameter.DATASET_KEY;
import static org.col.api.search.NameSearchParameter.ISSUE;
import static org.col.api.search.NameSearchParameter.RANK;
import static org.col.api.search.NameSearchParameter.*;

public class NameSearchRequestTranslatorTest {
 
  @Test
  public void test1() {
    
    NameSearchRequest nsr = new NameSearchRequest();
    Page page = new Page(15, 75);
    
    // Add facets + corresponding filters
    
    nsr.addFacet(ISSUE);
    nsr.addFacet(DATASET_KEY);
    nsr.addFacet(RANK);
    nsr.addFacet(STATUS);
    
    nsr.addFilter(ISSUE, Issue.ACCEPTED_ID_INVALID);
    nsr.addFilter(ISSUE, Issue.BASIONYM_ID_INVALID);
    nsr.addFilter(ISSUE, Issue.CHAINED_SYNONYM);
    
    nsr.addFilter(DATASET_KEY, 10);
    nsr.addFilter(DATASET_KEY, 12);
    
    nsr.addFilter(RANK, Rank.KINGDOM);
    
    // No filter for taxonomic status !
    
    // Add non-facet filters
    
    nsr.addFilter(NAME_ID, "ABCDEFG");

    nsr.setQ("anim");   
    nsr.setContent(EnumSet.of(SearchContent.AUTHORSHIP,SearchContent.VERNACULAR_NAME));
    
    NameSearchRequestTranslator t = new NameSearchRequestTranslator(nsr, page);
    
    EsSearchRequest esr = t.translate();
    
    System.out.println(serialize(esr));
    
  }
  
  
  private static String serialize(Object obj) {
    try {
      return EsModule.QUERY_WRITER.withDefaultPrettyPrinter().writeValueAsString(obj);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }


}
