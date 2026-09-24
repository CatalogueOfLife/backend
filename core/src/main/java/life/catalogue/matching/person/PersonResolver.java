package life.catalogue.matching.person;

import life.catalogue.api.model.Person;
import life.catalogue.api.vocab.TaxGroup;

import org.gbif.nameparser.api.NomCode;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

/**
 * Resolves an author citation to the persons of the registry it may name under the code of the name, and narrows them by
 * what is known about the name: its year and its taxonomic group. A person without years or groups is never ruled out.
 * The candidates of a citation are cached per code, the narrowing runs on every call.
 */
public class PersonResolver {
  private static final Pattern YEAR = Pattern.compile("(?<!\\d)(1[5-9]\\d\\d|20\\d\\d)(?!\\d)");

  /**
   * @param minAge      nobody publishes a name before this age
   * @param posthumous  years after a death a name may still appear under the dead
   * @param activeSlack years around the active years of a person without life dates
   */
  public record Margins(int minAge, int posthumous, int activeSlack) {
    public static final Margins DEFAULT = new Margins(10, 20, 15);
  }

  private final MemoryPersonStore registry;
  private final Margins margins;
  private final Map<String, Set<Person>> candidates = new ConcurrentHashMap<>();

  public PersonResolver(MemoryPersonStore registry, Margins margins) {
    this.registry = registry;
    this.margins = margins;
  }

  /**
   * @param citation an author as cited, or as {@link life.catalogue.common.tax.AuthorshipNormalizer} normalized it, which
   *                 folds to the same key
   * @return the persons the citation may name, empty for none or if every one was ruled out
   */
  public Set<Person> resolve(String citation, @Nullable NomCode code, @Nullable Integer year, @Nullable TaxGroup group) {
    Set<Person> all = candidates.computeIfAbsent((code == null ? "" : code.name()) + '|' + citation,
      k -> Collections.unmodifiableSet(registry.candidates(citation, code)));
    if (all.isEmpty() || (year == null && group == null)) {
      return all;
    }
    return all.stream().filter(p -> possible(p, year, group)).collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private boolean possible(Person p, @Nullable Integer year, @Nullable TaxGroup group) {
    if (year != null) {
      if (p.born() != null && year < p.born() + margins.minAge()) return false;
      if (p.died() != null && year > p.died() + margins.posthumous()) return false;
      if (p.born() == null && p.activeFrom() != null && year < p.activeFrom() - margins.activeSlack()) return false;
      if (p.died() == null && p.activeTo() != null && year > p.activeTo() + margins.activeSlack()) return false;
    }
    return group == null || p.groups().isEmpty() || !p.groups().stream().allMatch(g -> g.isDisparateTo(group));
  }

  /**
   * @param year as the parser gives it: "1753", "1878 [1879]", "184?"
   * @return the first full year, null for none or an imprecise one
   */
  @Nullable
  public static Integer year(@Nullable String year) {
    if (year == null) return null;
    Matcher m = YEAR.matcher(year);
    return m.find() ? Integer.valueOf(m.group(1)) : null;
  }
}
