package life.catalogue.es.nu;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import life.catalogue.api.search.NameUsageSearchParameter;
import static life.catalogue.api.search.NameUsageSearchParameter.ALPHAINDEX;
import static life.catalogue.api.search.NameUsageSearchParameter.CATALOGUE_KEY;
import static life.catalogue.api.search.NameUsageSearchParameter.DATASET_KEY;
import static life.catalogue.api.search.NameUsageSearchParameter.DECISION_MODE;
import static life.catalogue.api.search.NameUsageSearchParameter.FIELD;
import static life.catalogue.api.search.NameUsageSearchParameter.FOSSIL;
import static life.catalogue.api.search.NameUsageSearchParameter.ISSUE;
import static life.catalogue.api.search.NameUsageSearchParameter.NAME_ID;
import static life.catalogue.api.search.NameUsageSearchParameter.NAME_INDEX_ID;
import static life.catalogue.api.search.NameUsageSearchParameter.NOM_CODE;
import static life.catalogue.api.search.NameUsageSearchParameter.NOM_STATUS;
import static life.catalogue.api.search.NameUsageSearchParameter.PUBLISHED_IN_ID;
import static life.catalogue.api.search.NameUsageSearchParameter.PUBLISHER_KEY;
import static life.catalogue.api.search.NameUsageSearchParameter.RANK;
import static life.catalogue.api.search.NameUsageSearchParameter.RECENT;
import static life.catalogue.api.search.NameUsageSearchParameter.SECTOR_DATASET_KEY;
import static life.catalogue.api.search.NameUsageSearchParameter.SECTOR_KEY;
import static life.catalogue.api.search.NameUsageSearchParameter.STATUS;
import static life.catalogue.api.search.NameUsageSearchParameter.TAXON_ID;
import static life.catalogue.api.search.NameUsageSearchParameter.TYPE;
import static life.catalogue.api.search.NameUsageSearchParameter.USAGE_ID;

/**
 * Maps a name search parameter the corresponding Elasticsearch field(s). In principle a name search parameter may be mapped to multiple
 * Elasticsearch fields, in which case the parameter's value is searched in all of these fields. In practice, though, we currently don't
 * have multiply-mapped name search parameters.
 */
public class NameUsageFieldLookup extends EnumMap<NameUsageSearchParameter, String[]> {

  private static final Logger LOG = LoggerFactory.getLogger(NameUsageFieldLookup.class);

  public static final NameUsageFieldLookup INSTANCE = new NameUsageFieldLookup();

  private NameUsageFieldLookup() {
    super(NameUsageSearchParameter.class);
    putSingle(USAGE_ID, "usageId");
    putSingle(DATASET_KEY, "datasetKey");
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
    putSingle(SECTOR_DATASET_KEY, "sectorDatasetKey");
    putSingle(TYPE, "type");
    putSingle(TAXON_ID, "classificationIds");
    putSingle(CATALOGUE_KEY, "decisions.catalogueKey");
    putSingle(DECISION_MODE, "decisions.mode");
    putSingle(ALPHAINDEX, "nameStrings.sciNameLetter");

    if (size() != NameUsageSearchParameter.values().length) {
      Set<NameUsageSearchParameter> all = new HashSet<>(List.of(NameUsageSearchParameter.values()));
      all.removeAll(keySet());
      String missing = all.stream().map(Enum::toString).collect(Collectors.joining(","));
      String msg = "Not all name search parameters mapped to document fields: " + missing;
      LOG.error(msg);
      throw new IllegalStateException(msg);
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
