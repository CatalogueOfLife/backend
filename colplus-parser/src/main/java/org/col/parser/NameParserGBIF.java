package org.col.parser;

import com.google.common.base.Splitter;
import org.col.api.Name;
import org.col.api.vocab.NamePart;
import org.col.api.vocab.NameType;
import org.col.api.vocab.Rank;
import org.col.api.vocab.VocabularyUtils;
import org.gbif.api.model.checklistbank.ParsedName;
import org.gbif.nameparser.GBIFNameParser;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Wrapper around the GBIF Name parser to deal with col Name and API.
 */
public class NameParserGBIF implements NameParser {
  private static final GBIFNameParser PARSER = new GBIFNameParser();
  private static final Splitter AUTHORTEAM_SPLITTER = Splitter.on(",").trimResults();


  public Optional<Name> parse(String name) {
    return parse(name, Rank.UNRANKED);
  }

  /**
   * Fully parses a name using #parse(String, Rank) but converts names that throw a UnparsableException
   * into ParsedName objects with the scientific name, rank and name type given.
   */
  public Optional<Name> parse(String name, Rank rank) {
    ParsedName pn = PARSER.parseQuietly(name, RankParser.convertToGbif(rank));
    Name n = new Name();
    n.setRank(rank);
    if (pn.getType() == org.gbif.api.vocabulary.NameType.DOUBTFUL) {
      //TODO: add somewhere... n.getIssues().put(Issue.UNUSUAL_CHARACTERS, null);
      n.setType(NameType.SCIENTIFIC);
    } else {
      n.setType(VocabularyUtils.convertEnum(NameType.class, pn.getType()));
    }
    if (pn.isParsed()) {
      n.setScientificName(pn.canonicalName());

      n.getAuthorship().setAuthors(splitAuthors(pn.getAuthorship()));
      n.getAuthorship().setYear(pn.getYear());
      n.getBasionymAuthorship().setAuthors(splitAuthors(pn.getBracketAuthorship()));
      n.getBasionymAuthorship().setYear(pn.getBracketYear());

      if (pn.isBinomial()) {
        n.setGenus(pn.getGenusOrAbove());
        n.setSpecificEpithet(pn.getSpecificEpithet());
      }
      n.setInfragenericEpithet(pn.getInfraGeneric());
      n.setInfraspecificEpithet(pn.getInfraSpecificEpithet());
      n.setNotho(VocabularyUtils.convertEnum(NamePart.class, pn.getNotho()));

    } else {
      //TODO: add somewhere... n.getIssues().put(Issue.UNPARSABLE, null);
      n.setScientificName(name);
    }
    return Optional.of(n);
  }

  private List<String> splitAuthors(String authors) {
    if (authors != null) {
      return AUTHORTEAM_SPLITTER.splitToList(authors);
    }
    return Collections.emptyList();
  }

}
