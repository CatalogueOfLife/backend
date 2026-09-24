package life.catalogue.matching.person.harvest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

/**
 * Structured names from what the authorities give. A family name is never split off a full name: the last word is
 * not the surname in "Geoffroy Saint-Hilaire" or "Ruiz López".
 */
final class Names {
  private static final Pattern SUFFIX = Pattern.compile("[\\s,]+(I{1,3}|IV|Jr|Sr)\\.?$");

  private Names() {
  }

  /**
   * @return the parts in the order they appear in the label, parts the label lacks last; null for none
   */
  @Nullable
  static String ordered(List<String> parts, @Nullable String label) {
    if (parts.isEmpty()) return null;
    List<String> sorted = new ArrayList<>(parts);
    if (label != null) {
      sorted.sort(Comparator.comparingInt(p -> {
        int idx = label.indexOf(p);
        return idx < 0 ? Integer.MAX_VALUE : idx;
      }));
    }
    return String.join(" ", sorted);
  }

  /**
   * Several family names of one person are alternatives - a maiden and a married name, a latinised one - unless the
   * label holds them together, as it does for "Ruiz López".
   *
   * @return the parts the label holds, in its order, else the first part; null for none
   */
  @Nullable
  static String family(List<String> parts, @Nullable String label) {
    if (parts.isEmpty()) return null;
    if (label != null) {
      List<String> held = parts.stream().filter(label::contains).toList();
      if (!held.isEmpty()) return ordered(held, label);
    }
    return parts.get(0);
  }

  /**
   * @return a generation (I to IV) or Jr./Sr. ending the label, null for none
   */
  @Nullable
  static String suffix(@Nullable String label) {
    if (label == null) return null;
    Matcher m = SUFFIX.matcher(label.trim());
    if (!m.find()) return null;
    String s = m.group(1);
    return s.equals("Jr") || s.equals("Sr") ? s + "." : s;
  }

  /**
   * @param ipniAlternativeName one of IPNI's alternative names, "Sowerby, James DeCarle"
   * @return "James DeCarle Sowerby", null for a blank one
   */
  @Nullable
  static String surnameFirst(String ipniAlternativeName) {
    String x = StringUtils.trimToNull(ipniAlternativeName);
    if (x == null) return null;
    int comma = x.indexOf(',');
    return comma < 0 ? x : (x.substring(comma + 1).trim() + " " + x.substring(0, comma).trim()).trim();
  }
}
