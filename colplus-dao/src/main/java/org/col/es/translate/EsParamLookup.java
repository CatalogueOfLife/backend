package org.col.es.translate;

import java.util.HashMap;

import org.col.api.search.NameSearchParameter;

import static org.col.api.search.NameSearchParameter.DATASET_KEY;
import static org.col.api.search.NameSearchParameter.FIELD;
import static org.col.api.search.NameSearchParameter.ISSUE;
import static org.col.api.search.NameSearchParameter.NAME_ID;
import static org.col.api.search.NameSearchParameter.NAME_INDEX_ID;
import static org.col.api.search.NameSearchParameter.NOM_STATUS;
import static org.col.api.search.NameSearchParameter.PUBLISHED_IN_ID;
import static org.col.api.search.NameSearchParameter.RANK;
import static org.col.api.search.NameSearchParameter.STATUS;
import static org.col.api.search.NameSearchParameter.TYPE;

/**
 * Reverse lookup table mapping fields to search parameters. Needed when parsing ES response.
 */
public class EsParamLookup extends HashMap<String, NameSearchParameter> {

  public static final EsParamLookup INSTANCE = new EsParamLookup();

  private EsParamLookup() {
    putSingle("datasetKey", DATASET_KEY);
    putSingle("nameFields", FIELD);
    putSingle("issues", ISSUE);
    putSingle("nameId", NAME_ID);
    putSingle("nameIndexId", NAME_INDEX_ID);
    putSingle("nomStatus", NOM_STATUS);
    putSingle("publishedInId", PUBLISHED_IN_ID);
    putSingle("rank", RANK);
    putSingle("status", STATUS);
    putSingle("type", TYPE);
  }

  private void putSingle(String field, NameSearchParameter param) {
    put(field.toLowerCase(), param);
  }

  public NameSearchParameter lookup(String field) {
    return get(field.toLowerCase());
  }

}
