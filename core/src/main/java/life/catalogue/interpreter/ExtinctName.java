package life.catalogue.interpreter;

import life.catalogue.api.model.Name;
import life.catalogue.common.tax.SciNameNormalizer;

import static org.apache.commons.lang3.StringUtils.trimToNull;

public class ExtinctName {
  public final String name;
  public final boolean extinct;
  public Name pname;

  public ExtinctName(String name) {
    boolean dagger = false;
    name = trimToNull(name);
    if (name != null) {
      var m = SciNameNormalizer.dagger.matcher(name);
      if (m.find()) {
        name = trimToNull(m.replaceAll(""));
        dagger = true;
      }
    }
    this.name = name;
    this.extinct = dagger;
  }
}
