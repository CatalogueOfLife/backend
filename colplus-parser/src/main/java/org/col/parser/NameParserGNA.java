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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Option;

import java.util.Optional;

/**
 * Wrapper around the GNA Name parser to deal with col Name and API.
 */
public class NameParserGNA implements NameParser {
  private static final Logger LOG = LoggerFactory.getLogger(NameParserGNA.class);

  public static final NameParser PARSER = new NameParserGNA();

  private final ScientificNameParser parser = ScientificNameParser.instance();

  @Override
  public Optional<Name> parse(String scientificName) throws UnparsableException {
    ScientificNameParser.Result sn = parser.fromString(scientificName);
    return Optional.of(convert(scientificName, sn));
  }

  private Name convert(String name, ScientificNameParser.Result result) throws UnparsableException {
    Name n = new Name();

    if (result.preprocessorResult().virus()) {
      n.setScientificName(name);
      n.setType(NameType.VIRUS);
    
    } else {
      try {
        ScientificName sn = result.scientificName();

        if (sn.surrogate()) {
          n.setScientificName(name);
          n.setType(NameType.PLACEHOLDER);

        } else if (sn.hybrid().isDefined() && (Boolean) sn.hybrid().get()) {
          n.setScientificName(name);
          n.setType(NameType.HYBRID);

        } else {
          Option<String> canonical = result.canonized(true);
          if (canonical.isDefined()) {
            n.setScientificName(canonical.get());
          } else {
            LOG.warn("canonical cant be parsed!");
            throw new UnparsableException(NameType.NO_NAME, name);
          }

          n.setType(typeFromQuality(sn.quality()));
          ScinameMap map = ScinameMap.create(result);

          Option<Epithet> authorship = Option.empty();
          Option<Epithet> uninomial = map.uninomial();
          if (uninomial.isDefined()) {
            // dont set anything, we use scientificName for uninomials
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

      } catch (UnparsableException e) {
        // rethrow UnparsableException as we throw these on purpose
        throw e;

      } catch (RuntimeException e) {
        // convert all other unhandled exceptions into UnparsableException
        throw new UnparsableException(NameType.NO_NAME, name);
      }      
    }
    return n;
  }

  private void setAuthorship(Name n, Option<Epithet> epi) {
    if (epi.isDefined() && epi.get().hasAuthorship()) {
      Authorship auth = epi.get().getAuthorship();
      n.getAuthorship().setCombinationAuthors(Lists.newArrayList(auth.getCombinationAuthors()));
      n.getAuthorship().setCombinationYear(auth.getCombinationYear());
      n.getAuthorship().setOriginalAuthors(Lists.newArrayList(auth.getBasionymAuthors()));
      n.getAuthorship().setOriginalYear(auth.getBasionymYear());
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
