package org.col.parser;

import com.google.common.collect.Lists;
import org.col.api.Name;
import org.col.api.vocab.NameType;
import org.col.api.vocab.Rank;
import org.col.parser.gna.Authorship;
import org.col.parser.gna.Epithet;
import org.col.parser.gna.GnaRankUtils;
import org.col.parser.gna.ScinameMap;
import org.globalnames.parser.ScientificName;
import org.globalnames.parser.ScientificNameParser;
import org.globalnames.parser.WarningInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Option;
import scala.collection.Iterator;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Wrapper around the GNA Name parser to deal with col Name and API.
 */
public class NameParserGNA implements NameParser {
  private static final Logger LOG = LoggerFactory.getLogger(NameParserGNA.class);

  public static final NameParser PARSER = new NameParserGNA();
  private final static Pattern PLACEHOLDER = Pattern.compile("(?:unnamed|mixed|unassigned|unallocated|unplaced|undetermined|unclassified|uncultured|unknown|unspecified|uncertain|incertae sedis|not assigned|awaiting allocation|temp|dummy)", Pattern.CASE_INSENSITIVE);

  private final ScientificNameParser parser = ScientificNameParser.instance();

  @Override
  public Optional<Name> parse(String scientificName) throws UnparsableException {
    Name n = preparse(scientificName);
    if (n == null) {
      ScientificNameParser.Result sn = parser.fromString(scientificName);
      n = convert(scientificName, sn);
    }
    return Optional.of(n);
  }

  /**
   * Catch known parsing oddities the GNA Parser does to expect or tries to capture.
   * E.g. placeholder names
   * @return a name instance or null if preparsing did not capture anything
   */
  private Name preparse(String name) {
    // placeholders names
    if (PLACEHOLDER.matcher(name).find()) {
      return build(NameType.PLACEHOLDER, name);
    }
    return null;
  }

  private static Name build(NameType type, String name) {
    Name n = new Name();
    n.setType(type);
    n.setScientificName(name);
    return n;
  }

  private Name convert(String name, ScientificNameParser.Result result) throws UnparsableException {
    Name n = new Name();
    n.setScientificName(name);

    Iterator<WarningInfo> iter = result.preprocessorResult().warnings().seq().iterator();
    while (iter.hasNext()) {
      WarningInfo warn = iter.next();
      LOG.warn(warn.toString());
    }

    if (result.preprocessorResult().noParse()) {
      throw new UnparsableException(NameType.NO_NAME, name);

    } else if (result.preprocessorResult().virus()) {
      n.setType(NameType.VIRUS);

    } else {
      ScientificName sn = result.scientificName();

      if (sn.surrogate()) {
        n.setType(NameType.PLACEHOLDER);

      } else if (sn.hybrid().isDefined() && (Boolean) sn.hybrid().get()) {
        n.setType(NameType.HYBRID);

      } else {

        ScinameMap map = ScinameMap.create(result);
        n.setType(typeFromQuality(sn.quality()));
        Option<Epithet> authorship = Option.empty();
        Option<Epithet> uninomial = map.uninomial();
        if (uninomial.isDefined()) {
          // we differ between uninomials and infragenerics that have a genus
          if (uninomial.get().hasParent()) {
            // infrageneric
            n.setGenus(uninomial.get().getParent());
            n.setInfragenericEpithet(uninomial.get().getEpithet());
            Rank rankCol = GnaRankUtils.inferRank(uninomial.get().getRank());
            n.setRank(rankCol);
            n.setScientificName(n.buildScientificName());

          } else {
            // use scientificName for uninomials
            n.setScientificName(uninomial.get().getEpithet());
          }
          //pn.setGenus(uninomial.get().getEpithet());
          authorship = uninomial;

        } else {
          // bi/trinomials do not come with a uninomial
          Option<Epithet> genus = map.genus();
          if (genus.isDefined()) {
            n.setGenus(genus.get().getEpithet());
            authorship = genus;
          }

          Option<Epithet> infraGenus = map.infraGeneric();
          if (infraGenus.isDefined()) {
            n.setInfragenericEpithet(infraGenus.get().getEpithet());
            authorship = infraGenus;
          }

          Option<Epithet> species = map.specificEpithet();
          if (species.isDefined()) {
            n.setSpecificEpithet(species.get().getEpithet());
            authorship = species;
            if (n.getRank().equals(Rank.UNRANKED)) {
              n.setRank(Rank.SPECIES);
            }
          }

          Option<Epithet> infraSpecies = map.infraSpecificEpithet();
          if (infraSpecies.isDefined()) {
            n.setInfraspecificEpithet(infraSpecies.get().getEpithet());
            Rank rankCol = GnaRankUtils.inferRank(infraSpecies.get().getRank());
            if (rankCol != null) {
              n.setRank(rankCol);
            } else if (n.getRank().equals(Rank.UNRANKED)) {
              n.setRank(Rank.INFRASPECIFIC_NAME);
            }
            authorship = infraSpecies;
          }
          // build canonical scientific name by our Name class - it uses different logic than the GNA Parser
          n.setScientificName(n.buildScientificName());
        }

        // set authorship from the lowest epithet
        setAuthorship(n, authorship);

        //TODO: see if we can handle annotations, do they map to ParsedName at all ???
        //Optional anno = map.annotation();
        //if (anno.isPresent()) {
        //  System.out.println(anno.get().getClass());
        //  System.out.println(anno.get());
        //}
      }
    }
    return n;
  }

  private void setAuthorship(Name n, Option<Epithet> epi) {
    if (epi.isDefined() && epi.get().hasAuthorship()) {
      Authorship auth = epi.get().getAuthorship();
      n.getAuthorship().setCombinationAuthors(Lists.newArrayList(auth.getCombinationAuthors()));
      n.getAuthorship().setCombinationYear(auth.getCombinationYear());
      n.getAuthorship().setBasionymAuthors(Lists.newArrayList(auth.getBasionymAuthors()));
      n.getAuthorship().setBasionymYear(auth.getBasionymYear());
    }
  }

  private static NameType typeFromQuality(Integer quality) {
    //TODO: log issues in case of 3 and higher???
    switch (quality) {
      case 1: return NameType.SCIENTIFIC;
      case 2: return NameType.SCIENTIFIC;
      case 3: return NameType.SCIENTIFIC;
    }
    return NameType.NO_NAME;
  }

}
