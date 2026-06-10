package life.catalogue.parser;


import life.catalogue.api.vocab.DegreeOfEstablishment;
import life.catalogue.common.text.StringUtils;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Parses the TDWG degree of establishment vocabulary https://dwc.tdwg.org/doe/
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

  public DegreeOfEstablishmentParser() {
    super("degreeofestablishment.csv", DegreeOfEstablishment.class);
  }

  @Override
  protected boolean isEmpty(String normalisedValue) {
    return NON_DEGREES.contains(normalisedValue);
  }

}
