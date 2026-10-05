package life.catalogue.assembly;

import life.catalogue.api.model.FormattableName;

import java.util.*;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Names a merge must never bring in. Names match case insensitively against the full label with authorship or the
 * bare scientific name, patterns are case insensitive regular expressions searched anywhere in the label.
 */
public class NameBlocklist {
  private static final Logger LOG = LoggerFactory.getLogger(NameBlocklist.class);
  private final Set<String> names = new HashSet<>();
  private final List<Pattern> patterns = new ArrayList<>();

  public NameBlocklist(@Nullable Collection<String> names, @Nullable Collection<String> patterns) {
    if (names != null) {
      for (String n : names) {
        if (!StringUtils.isBlank(n)) {
          this.names.add(norm(n));
        }
      }
    }
    if (patterns != null) {
      for (String p : patterns) {
        if (!StringUtils.isBlank(p)) {
          try {
            this.patterns.add(Pattern.compile(p.trim(), Pattern.CASE_INSENSITIVE));
          } catch (IllegalArgumentException e) {
            LOG.warn("Invalid name pattern: " + p, e);
          }
        }
      }
    }
  }

  public boolean isEmpty() {
    return names.isEmpty() && patterns.isEmpty();
  }

  public boolean isBlocked(FormattableName n) {
    if (names.contains(norm(n.getLabel())) || names.contains(norm(n.getScientificName()))) {
      return true;
    }
    for (Pattern p : patterns) {
      if (p.matcher(n.getLabel()).find()) {
        return true;
      }
    }
    return false;
  }

  private static String norm(String x) {
    return x == null ? null : x.trim().toUpperCase();
  }
}
