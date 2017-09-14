package org.col.parser;

import org.col.api.Name;
import org.col.api.vocab.NamePart;
import org.col.api.vocab.Rank;
import org.col.api.vocab.VocabularyUtils;
import org.gbif.api.model.checklistbank.ParsedName;
import org.gbif.nameparser.GBIFNameParser;

import java.util.Optional;

/**
 * Wrapper around the GBIF Name parser to deal with col Name and API.
 */
public class NameParser {
  private static final GBIFNameParser PARSER = new GBIFNameParser();

  /**
   * Fully parses a name using #parse(String, Rank) but converts names that throw a UnparsableException
   * into ParsedName objects with the scientific name, rank and name type given.
   */
  public Name parse(String name, Optional<Rank> rank) {
    ParsedName pn = PARSER.parseQuietly(name, VocabularyUtils.convertToGbif(rank));
    Name n = new Name();
    n.setScientificName(name);
    n.setCanonicalName(pn.canonicalName());
    n.setAuthorship(pn.authorshipComplete());
    n.setMonomial(pn.getGenusOrAbove());
    n.setEpithet(pn.getSpecificEpithet());
    n.setInfraEpithet(pn.getInfraSpecificEpithet());
    n.setNotho(VocabularyUtils.convertEnum(NamePart.class, pn.getNotho()));
    n.setParsed(pn.isParsed());
    n.setPublishedInYear(pn.getYearInt());
    rank.ifPresent(n::setRank);
    return n;
  }

  /**
   * parses the name without authorship and returns the ParsedName.canonicalName() string
   *
   * @param rank the rank of the name if it is known externally. Helps identifying infrageneric names vs bracket authors
   */
  public String parseToCanonical(String scientificName, Optional<Rank> rank) {
    return PARSER.parseToCanonical(scientificName, VocabularyUtils.convertToGbif(rank));
  }

}
