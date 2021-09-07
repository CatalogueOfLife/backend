package life.catalogue.dao;

import life.catalogue.api.model.DSID;

import org.gbif.nameparser.api.Rank;

/**
 * Simple interface for counting all included, accepted taxa of a given rank, e.g. species, that
 * are descendants of a given taxonID.
 *
 * We abstract this in an interface to allow simple implementation from Elastic Search or Postgres.
 */
public interface TaxonCounter {

  /**
   * Count all included, accepted taxa of a given rank that have the given taxonID in their parental classification.
   * @param taxonID parent taxon to require in classification
   * @param countRank rank if descendants to count, e.g. species
   */
  int count(DSID<String> taxonID, Rank countRank);
}
