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
import static org.col.api.search.NameSearchParameter.TAXON_ID;
import static org.col.api.search.NameSearchParameter.*;

/**
 * Maps a name search parameter the corresponding Elasticsearch field(s). In principle a name search parameter may be mapped to multiple
 * Elasticsearch fields, in which case the parameter's value is searched in all of these fields. In practice, though, we currently don't
 * have multiply-mapped name search parameters.
 */
public class EsFieldLookup extends EnumMap<NameSearchParameter, String[]> {

  public static final EsFieldLookup INSTANCE = new EsFieldLookup();

  private EsFieldLookup() {
    super(NameSearchParameter.class);
    putSingle(DATASET_KEY, "datasetKey");
    putSingle(DECISION_KEY, "decisionKey");
    putSingle(FIELD, "nameFields");
    putSingle(ISSUE, "issues");
    putSingle(NAME_ID, "nameId");
    putSingle(NAME_INDEX_ID, "nameIndexId");
    putSingle(NOM_STATUS, "nomStatus");
    putSingle(PUBLISHED_IN_ID, "publishedInId");
    putSingle(PUBLISHER_KEY, "publisherKey");
    putSingle(RANK, "rank");
    putSingle(STATUS, "status");
    putSingle(TYPE, "type");
    putSingle(TAXON_ID, "classificationIds");
  }

  public String lookup(NameSearchParameter param) {
    String[] fields = get(param);
    // Currently every NameSearchParameter maps to just one field in the name usage document
    return fields[0];
  }

  private void putSingle(NameSearchParameter param, String field) {
    put(param, new String[] {field});
  }

}
