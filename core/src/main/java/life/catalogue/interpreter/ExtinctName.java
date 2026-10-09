package life.catalogue.interpreter;

import life.catalogue.api.model.Name;
import life.catalogue.common.tax.SciNameNormalizer;

import java.util.regex.Pattern;

import static org.apache.commons.lang3.StringUtils.trimToNull;

public class ExtinctName {
  // stricter than SciNameNormalizer.removeHybridSignGenus, which builds names index keys: in a field of its own a
  // letter x is a hybrid sign only when followed by a space - XBB1006 is a genus, not the hybrid BB1006
  private static final Pattern HYBRID_SIGN_GENUS = Pattern.compile("^\\s*(?:×\\s*|[xX]\\s+)([A-Z])");

  public final String name;
  public final boolean extinct;
  public final boolean hybrid;
  public Name pname;

  public ExtinctName(String name) {
    boolean dagger = false;
    boolean hybrid = false;
    name = trimToNull(name);
    if (name != null) {
      var m = SciNameNormalizer.dagger.matcher(name);
      if (m.find()) {
        name = trimToNull(m.replaceAll(""));
        dagger = true;
      }
    }
    // a dagger can be all there was
    if (name != null) {
      var m = HYBRID_SIGN_GENUS.matcher(name);
      if (m.find()) {
        name = trimToNull(m.replaceFirst("$1"));
        hybrid = true;
      } else {
        m = SciNameNormalizer.removeHybridSignEpithet.matcher(name);
        if (m.find()) {
          name = trimToNull(m.replaceFirst("$1"));
          hybrid = true;
        }
      }
    }
    this.name = name;
    this.extinct = dagger;
    this.hybrid = hybrid;
  }
}
