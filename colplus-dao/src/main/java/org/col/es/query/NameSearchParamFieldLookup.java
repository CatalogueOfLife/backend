package org.col.es.query;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;

import org.col.api.search.NameSearchParameter;
import static org.col.api.search.NameSearchParameter.*;

public class NameSearchParamFieldLookup extends EnumMap<NameSearchParameter, List<String>> {

  private NameSearchParamFieldLookup() {
    super(NameSearchParameter.class);
    put(DATASET_KEY, "datasetKey");
    put(FIELD, "nameFields");
    put(ISSUE, "issues");
    put(NAME_ID, "nameId");
    put(NAME_INDEX_ID, "nameIndexId");
    put(NOM_STATUS, "nomStatus");
    put(PUBLISHED_IN_ID, "publishedInId");
    put(RANK, "rank");
    put(STATUS, "status");
    put(TYPE, "type");
  }

  private void put(NameSearchParameter nsp, String path) {
    super.put(nsp, Arrays.asList(path));
  }

}
