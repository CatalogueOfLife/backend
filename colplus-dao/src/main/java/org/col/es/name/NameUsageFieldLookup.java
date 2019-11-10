package org.col.es.name;

import java.util.EnumMap;

import org.col.api.search.NameUsageSearchParameter;

import static org.col.api.search.NameUsageSearchParameter.DATASET_KEY;
import static org.col.api.search.NameUsageSearchParameter.DECISION_KEY;
import static org.col.api.search.NameUsageSearchParameter.FIELD;
import static org.col.api.search.NameUsageSearchParameter.FOSSIL;
import static org.col.api.search.NameUsageSearchParameter.ISSUE;
import static org.col.api.search.NameUsageSearchParameter.NAME_ID;
import static org.col.api.search.NameUsageSearchParameter.NAME_INDEX_ID;
import static org.col.api.search.NameUsageSearchParameter.NOM_CODE;
import static org.col.api.search.NameUsageSearchParameter.NOM_STATUS;
import static org.col.api.search.NameUsageSearchParameter.PUBLISHED_IN_ID;
import static org.col.api.search.NameUsageSearchParameter.PUBLISHER_KEY;
import static org.col.api.search.NameUsageSearchParameter.RANK;
import static org.col.api.search.NameUsageSearchParameter.RECENT;
import static org.col.api.search.NameUsageSearchParameter.SECTOR_KEY;
import static org.col.api.search.NameUsageSearchParameter.STATUS;
import static org.col.api.search.NameUsageSearchParameter.TAXON_ID;
import static org.col.api.search.NameUsageSearchParameter.TYPE;
import static org.col.api.search.NameUsageSearchParameter.USAGE_ID;

/**
 * Maps a name search parameter the corresponding Elasticsearch field(s). In principle a name search parameter may be
 * mapped to multiple Elasticsearch fields, in which case the parameter's value is searched in all of these fields. In
 * practice, though, we currently don't have multiply-mapped name search parameters.
 */
public class NameUsageFieldLookup extends EnumMap<NameUsageSearchParameter, String[]> {

  public static final NameUsageFieldLookup INSTANCE = new NameUsageFieldLookup();

  private NameUsageFieldLookup() {
    super(NameUsageSearchParameter.class);
    putSingle(USAGE_ID, "usageId");
    putSingle(DATASET_KEY, "datasetKey");
    putSingle(DECISION_KEY, "decisionKey");
    putSingle(FIELD, "nameFields");
    putSingle(FOSSIL, "fossil");
    putSingle(ISSUE, "issues");
    putSingle(NAME_ID, "nameId");
    putSingle(NAME_INDEX_ID, "nameIndexId");
    putSingle(NOM_CODE, "nomCode");
    putSingle(NOM_STATUS, "nomStatus");
    putSingle(PUBLISHED_IN_ID, "publishedInId");
    putSingle(PUBLISHER_KEY, "publisherKey");
    putSingle(RANK, "rank");
    putSingle(RECENT, "recent");
    putSingle(STATUS, "status");
    putSingle(SECTOR_KEY, "sectorKey");
    putSingle(TYPE, "type");
    putSingle(TAXON_ID, "classificationIds");
    if (size() != NameUsageSearchParameter.values().length) {
      throw new IllegalStateException("Not all name search parameters mapped to document fields");
    }
  }

  public String lookup(NameUsageSearchParameter param) {
    String[] fields = get(param);
    // Currently every NameSearchParameter maps to just one field in the name usage document
    return fields[0];
  }

  private void putSingle(NameUsageSearchParameter param, String field) {
    put(param, new String[] {field});
  }

}
