package life.catalogue.parser;

import life.catalogue.api.vocab.TypeStatus;
import life.catalogue.common.text.StringUtils;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 *
 */
public class TypeStatusParser extends EnumParser<TypeStatus> {
  public static final TypeStatusParser PARSER = new TypeStatusParser();
  private static final Pattern EX_TYPES = Pattern.compile("^EX.+TYP(E|O|US)$");
  private static final Pattern PARA_TYPES = Pattern.compile("^PARA.+TYP(E|O|US)$");
  private static final Pattern ISO_TYPES = Pattern.compile("^ISO.+TYP(E|O|US)$");
  private static final Set<String> NON_TYPES = List.of("other material", "material", "material cited", "none", "no", "no type",
      "preserved specimen", "preserved")
    .stream()
    .map(String::toUpperCase)
    .map(StringUtils::digitOrAsciiLetters)
    .map(x -> x.replaceAll(" +", "")) // the map parser normalise method does it
    .collect(Collectors.toSet());

  public TypeStatusParser() {
    super("typestatus.csv", TypeStatus.class);
    for (TypeStatus t : TypeStatus.values()) {
      if (t.name().endsWith("TYPE")) {
        String base = t.name().replaceFirst("TYPE$", "").toLowerCase();
        add(base, t);
        add(base+"typ", t);
        add(base+"typo", t);
        add(base+"types", t);
        add(base+"typus", t);
      }
    }
  }

  @Override
  protected boolean isEmpty(String normalisedValue) {
    return NON_TYPES.contains(normalisedValue);
  }

  @Override
  TypeStatus parseKnownValues(String upperCaseValue) {
    TypeStatus st = super.parseKnownValues(upperCaseValue);
    if (st == null) {
      // try some regex so we dont have to maintain all mappings manually
      if (EX_TYPES.matcher(upperCaseValue).find()) {
        return TypeStatus.EX_TYPE;
      }
      if (PARA_TYPES.matcher(upperCaseValue).find()) {
        return TypeStatus.PARATYPE;
      }
    }
    return st;
  }
}
