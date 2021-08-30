package life.catalogue.db.tree;

import life.catalogue.api.model.DSID;

import org.gbif.nameparser.api.Rank;

/**
 * Simple interface for counting all included, accepted taxa of a given rank, e.g. species, that
 * are descendants of a given taxonID.
 *
 * We abstract this in an interface to allow simple implementation from Elastic Search or Postgres.
 */
public interface TaxonCounter {

  int count(DSID<String> taxonID, Rank countRank);
}
