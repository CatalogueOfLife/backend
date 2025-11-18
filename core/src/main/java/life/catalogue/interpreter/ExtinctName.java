package life.catalogue.interpreter;

import life.catalogue.api.model.Name;
import life.catalogue.common.tax.SciNameNormalizer;

import static org.apache.commons.lang3.StringUtils.trimToNull;

public class ExtinctName {
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
      m = SciNameNormalizer.removeHybridSignGenus.matcher(name);
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
