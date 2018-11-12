package org.col.api.model;

import java.util.Arrays;
import java.util.HashSet;

import com.fasterxml.jackson.core.JsonProcessingException;

import org.col.api.jackson.ApiModule;
import org.col.api.jackson.SerdeTestBase;
import org.col.api.search.NameSearchParameter;
import org.col.api.search.NameSearchRequest;
import org.col.api.vocab.NomStatus;

/**
 *
 */
public class NameSearchRequestSerde extends SerdeTestBase<NameSearchRequest> {
  
  public NameSearchRequestSerde() {
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
  
  protected void debug(String json, Wrapper<NameSearchRequest> wrapper, Wrapper<NameSearchRequest> wrapper2) {
    try {
      System.out.println(ApiModule.MAPPER.writeValueAsString(wrapper.value));
      System.out.println(ApiModule.MAPPER.writeValueAsString(wrapper2.value));
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
  }
}