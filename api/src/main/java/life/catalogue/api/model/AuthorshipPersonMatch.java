package life.catalogue.api.model;

import java.util.List;

/**
 * An authorship split by the name parser, every author matched to the persons of the registry. Each slot holds one match
 * per author, in the order of the authorship; all are empty for an authorship the parser cannot read.
 */
public record AuthorshipPersonMatch(String authorship, Status status, List<PersonMatch> combination,
                                    List<PersonMatch> combinationEx, List<PersonMatch> basionym, List<PersonMatch> basionymEx,
                                    List<PersonMatch> sanctioning) {

  public enum Status {
    PARSED,
    UNPARSABLE
  }
}
