package life.catalogue.parser;


import life.catalogue.api.vocab.EstablishmentMeans;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Parses establishment means. Several values separated by a semicolon or pipe, as GRIIS has
 * "introduced (alien, exotic, non-native, nonindigenous); introduced: assisted colonization", are accepted
 * if they lie on one line of the vocabulary hierarchy, and the most specific of them is returned.
 */
public class EstablishmentMeansParser extends EnumParser<EstablishmentMeans> {
  public static final EstablishmentMeansParser PARSER = new EstablishmentMeansParser();
  private static final Pattern MULTI = Pattern.compile("[;|]");

  public EstablishmentMeansParser() {
    super("establishmentmeans.csv", EstablishmentMeans.class);
  }

  @Override
  public Optional<? extends EstablishmentMeans> parse(String value) throws UnparsableException {
    if (value == null || !MULTI.matcher(value).find()) {
      return super.parse(value);
    }
    EstablishmentMeans best = null;
    for (String part : MULTI.split(value)) {
      var em = super.parse(part);
      if (em.isPresent()) {
        if (best == null || em.get().getParent() == best) {
          best = em.get();
        } else if (em.get() != best && best.getParent() != em.get()) {
          throw new UnparsableException(EstablishmentMeans.class, value);
        }
      }
    }
    return Optional.ofNullable(best);
  }

}
