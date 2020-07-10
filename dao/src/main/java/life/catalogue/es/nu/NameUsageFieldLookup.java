package life.catalogue.es.nu;

import life.catalogue.api.search.NameUsageSearchParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static life.catalogue.api.search.NameUsageSearchParameter.*;

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
    putSingle(ALPHAINDEX, "nameStrings.sciNameLetter");
    putSingle(AUTHORSHIP, "authorship");
    putSingle(AUTHORSHIP_YEAR, "authorshipYear");
    putSingle(CATALOGUE_KEY, "decisions.catalogueKey");
    putSingle(DATASET_KEY, "datasetKey");
    putSingle(DECISION_MODE, "decisions.mode");
    putSingle(FIELD, "nameFields");
    putSingle(FOSSIL, "fossil");
    putSingle(ISSUE, "issues");
    putSingle(LIFEZONE, "lifezone");
    putSingle(NAME_ID, "nameId");
    putSingle(NAME_INDEX_ID, "nameIndexIds");
    putSingle(NOM_CODE, "nomCode");
    putSingle(NOM_STATUS, "nomStatus");
    putSingle(PUBLISHED_IN_ID, "publishedInId");
    putSingle(PUBLISHER_KEY, "publisherKey");
    putSingle(RANK, "rank");
    putSingle(RECENT, "recent");
    putSingle(STATUS, "status");
    putSingle(SECTOR_DATASET_KEY, "sectorDatasetKey");
    putSingle(SECTOR_KEY, "sectorKey");
    putSingle(TAXON_ID, "classificationIds");
    putSingle(TYPE, "type");
    putSingle(USAGE_ID, "usageId");

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
