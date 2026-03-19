package life.catalogue.es.query;

import life.catalogue.api.search.NameUsageSearchParameter;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static life.catalogue.api.search.NameUsageSearchParameter.*;

/**
 * Maps a name search parameter the corresponding Elasticsearch field(s). In principle a name search parameter may be mapped to multiple
 * Elasticsearch fields, in which case the parameter's value is searched in all of these fields. In practice, though, we currently don't
 * have multiply-mapped name search parameters.
 */
public class FieldLookup extends EnumMap<NameUsageSearchParameter, String[]> {

  private static final Logger LOG = LoggerFactory.getLogger(FieldLookup.class);

  public static final FieldLookup INSTANCE = new FieldLookup();

  private FieldLookup() {
    super(NameUsageSearchParameter.class);
    putSingle(ALPHAINDEX, "usage.name.alphaIndex");
    putSingle(AUTHORSHIP, "usage.name.author"); // this is the keyword/facet field for individual authors, not the entire authorship string which a content=authorship search works on!
    putSingle(AUTHORSHIP_YEAR, "usage.name.combinationAuthorship.year");
    putSingle(CATALOGUE_KEY, "decisions.datasetKey");
    putSingle(DATASET_KEY, "usage.name.datasetKey");
    putSingle(DECISION_MODE, "decisions.mode");
    putSingle(ENVIRONMENT, "usage.environments");
    putSingle(EXTINCT, "usage.extinct");
    putSingle(FIELD, "usage.nameFields");
    putSingle(GROUP, "group");
    putSingle(ISSUE, "issues");
    putSingle(NAME_ID, "usage.name.id");
    putSingle(NAME_TYPE, "usage.name.type");
    putSingle(NOM_CODE, "usage.name.code");
    putSingle(NOM_STATUS, "usage.name.nomStatus");
    putSingle(ORIGIN, "usage.origin");
    putSingle(PUBLISHED_IN_ID, "usage.name.publishedInId");
    putSingle(RANK, "usage.name.rank");
    putSingle(SECONDARY_SOURCE, "secondarySourceKeys");
    putSingle(SECONDARY_SOURCE_GROUP, "secondarySourceGroups");
    putSingle(SECTOR_DATASET_KEY, "sectorDatasetKey");
    putSingle(SECTOR_KEY, "sectorKey");
    putSingle(SECTOR_MODE, "sectorMode");
    putSingle(SECTOR_PUBLISHER_KEY, "sectorPublisherKey");
    putSingle(STATUS, "usage.status");
    putSingle(TAXON_ID, "classification.id");
    putSingle(USAGE_ID, "id");

    if (size() != NameUsageSearchParameter.values().length) {
      Set<NameUsageSearchParameter> all = new HashSet<>(List.of(NameUsageSearchParameter.values()));
      all.removeAll(keySet());
      String missing = all.stream().map(Enum::toString).collect(Collectors.joining(","));
      String msg = "Not all name search parameters mapped to document fields: " + missing;
      LOG.error(msg);
      throw new IllegalStateException(msg);
    }
  }

  public String lookupSingle(NameUsageSearchParameter param) {
    // every NameSearchParameter maps to just one field in the name usage document
    return get(param)[0];
  }

  public String[] lookup(NameUsageSearchParameter param) {
    return get(param);
  }

  private void putSingle(NameUsageSearchParameter param, String field) {
    put(param, new String[] {field});
  }

}
