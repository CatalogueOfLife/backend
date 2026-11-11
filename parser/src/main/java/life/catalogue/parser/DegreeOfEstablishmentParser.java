package life.catalogue.parser;


import life.catalogue.api.vocab.DegreeOfEstablishment;
import life.catalogue.common.text.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static life.catalogue.api.vocab.DegreeOfEstablishment.*;

/**
 * Parses the TDWG degree of establishment vocabulary https://dwc.tdwg.org/doe/
 * including the category codes of Blackburn et al. 2011 it is based on, alone or as in GRIIS
 * "widespread invasive (category E)". A known code decides over the text next to it.
 */
public class DegreeOfEstablishmentParser extends EnumParser<DegreeOfEstablishment> {
  public static final DegreeOfEstablishmentParser PARSER = new DegreeOfEstablishmentParser();

  // WoRMS uses its own Invasiveness vocabulary (http://www.marinespecies.org/traits/Invasiveness) for the
  // degree of establishment. Apart from "Invasive" none of its values correspond to a TDWG degree of
  // establishment, so we accept them silently as empty instead of flagging them as invalid.
  // "NA"/"N/A" are common not-applicable placeholders, e.g. in GRIIS archives.
  // https://github.com/CatalogueOfLife/backend/issues/1511
  private static final Set<String> NON_DEGREES = List.of(
      "not invasive", "of concern", "management recorded",
      "uncertain", "invasiveness uncertain", "invasiveness not specified", "not specified",
      "na")
    .stream()
    .map(String::toUpperCase)
    .map(StringUtils::digitOrAsciiLetters)
    .map(x -> x.replaceAll(" +", "")) // the map parser normalise method does it
    .collect(Collectors.toSet());

  // category A, not transported beyond its native range, has no degree of establishment
  private static final Map<String, DegreeOfEstablishment> CATEGORIES = Map.of(
    "B1", CAPTIVE,
    "B2", CULTIVATED,
    "B3", RELEASED,
    "C0", FAILING,
    "C1", CASUAL,
    "C2", REPRODUCING,
    "C3", ESTABLISHED,
    "D1", COLONISING,
    "D2", INVASIVE,
    "E", WIDESPREAD_INVASIVE
  );
  private static final Pattern CATEGORY = Pattern.compile("^\\s*([A-E][0-3]?)\\s*$|\\bcategory\\s*([A-E][0-3]?)\\b",
    Pattern.CASE_INSENSITIVE);

  public DegreeOfEstablishmentParser() {
    super("degreeofestablishment.csv", DegreeOfEstablishment.class);
  }

  @Override
  public Optional<? extends DegreeOfEstablishment> parse(String value) throws UnparsableException {
    if (value != null) {
      Matcher m = CATEGORY.matcher(value);
      if (m.find()) {
        String code = (m.group(1) != null ? m.group(1) : m.group(2)).toUpperCase();
        if (CATEGORIES.containsKey(code)) {
          return Optional.of(CATEGORIES.get(code));
        }
      }
    }
    return super.parse(value);
  }

  @Override
  protected boolean isEmpty(String normalisedValue) {
    return NON_DEGREES.contains(normalisedValue);
  }

}
