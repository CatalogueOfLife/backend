package life.catalogue.parser;

import life.catalogue.api.vocab.TypeStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

/**
 *
 */
public class TypeStatusParser extends EnumParser<TypeStatus> {
  private static final Logger LOG = LoggerFactory.getLogger(TypeStatusParser.class);
  
  public static final TypeStatusParser PARSER = new TypeStatusParser();
  private static final Pattern EX_TYPES = Pattern.compile("^EX.+TYP(E|O|US)$");
  private static final Pattern PARA_TYPES = Pattern.compile("^PARA.+TYP(E|O|US)$");
  private static final Pattern ISO_TYPES = Pattern.compile("^ISO.+TYP(E|O|US)$");

  public TypeStatusParser() {
    super("typestatus.csv", TypeStatus.class);
    for (TypeStatus t : TypeStatus.values()) {
      if (t.name().endsWith("TYPE")) {
        String base = t.name().replaceFirst("TYPE$", "").toLowerCase();
        add(base, t);
        add(base+"typo", t);
        add(base+"typus", t);
      }
    }
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
