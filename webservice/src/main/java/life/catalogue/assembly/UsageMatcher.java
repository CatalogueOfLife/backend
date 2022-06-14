package life.catalogue.assembly;

import life.catalogue.api.model.NameUsageBase;
import life.catalogue.matching.NameIndex;

import org.apache.ibatis.session.SqlSessionFactory;

import java.util.List;

/**
 * Matches usages against a given dataset. Matching is primarily based on names index matches,
 * but implements some further logic for canonical names and cross code homonyms.
 *
 * Matches are retrieved from the database and are cached in particular for uninomials / higher taxa.
 */
public class UsageMatcher {
  private final int datasetKey;
  private final NameIndex nameIndex;
  private final SqlSessionFactory factory;

  public UsageMatcher(int datasetKey, NameIndex nameIndex, SqlSessionFactory factory) {
    this.datasetKey = datasetKey;
    this.nameIndex = nameIndex;
    this.factory = factory;
  }

  public NameUsageBase match(NameUsageBase nu, List<NameUsageBase> parents) {

  }
}
