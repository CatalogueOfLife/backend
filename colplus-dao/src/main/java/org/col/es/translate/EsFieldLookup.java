package org.col.es.translate;

import java.util.EnumMap;

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
 * Maps a name search parameter the corresponding Elasticsearch field(s). In principle a name search
 * parameter may be mapped to multiple Elasticsearch fields, in which case the parameter's value is
 * searched in all of these fields. In practice, though, we currently don't have multiply-mapped
 * name search parameters.
 */
public class EsFieldLookup extends EnumMap<NameSearchParameter, String[]> {

  public static final EsFieldLookup INSTANCE = new EsFieldLookup();

  private EsFieldLookup() {
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

  private void put(NameSearchParameter key, String val) {
    super.put(key, new String[] {val});
  }

}
