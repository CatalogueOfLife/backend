package life.catalogue.matching.person;

import life.catalogue.api.model.Person;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.matching.Equality;
import life.catalogue.matching.authorship.AuthorContext;
import life.catalogue.matching.authorship.AuthorMatcher;
import life.catalogue.matching.authorship.AuthorTeam;

import java.util.*;
import java.util.function.Consumer;

import javax.annotation.Nullable;

/**
 * Compares authors as persons. Every author of a team resolves to the persons of the registry it may name, narrowed by
 * the year and group of the name. Two authors are EQUAL when they share a person and DIFFERENT when they name unrelated
 * persons; relatives - a parent, a child, a sibling - get the {@link RelativesPolicy}. An author that resolves to
 * nobody falls back to the string comparison, against every form of the persons on the other side. Teams keep the rule
 * of the string comparison: any author of one that is any author of the other makes them EQUAL.
 * <p>
 * Identity does not depend on the mode, only the fallback does.
 */
public class PersonAuthorMatcher implements AuthorMatcher {

  /**
   * What two relatives are. The matcher cannot see recurrence: sources cite the same act under the father in one and
   * the son in the other, and UNKNOWN does not split what they confuse.
   */
  public enum RelativesPolicy {
    UNKNOWN(Equality.UNKNOWN),
    DIFFERENT(Equality.DIFFERENT);

    final Equality verdict;

    RelativesPolicy(Equality verdict) {
      this.verdict = verdict;
    }
  }

  /** what decided a pair of authors */
  public enum Rule {
    /** the two teams are the same strings */
    IDENTICAL,
    /** both authors resolved: a shared person, or unrelated persons */
    IDENTITY,
    /** both resolved, to related persons only */
    RELATIVES,
    /** an author resolved to nobody, the strings decided */
    FALLBACK
  }

  public record Decision(String author1, String author2, Set<Person> persons1, Set<Person> persons2, Rule rule, Equality verdict) {
  }

  public record Explanation(AuthorTeam team1, AuthorTeam team2, AuthorContext context, Equality verdict, List<Decision> decisions) {
  }

  private final MemoryPersonStore registry;
  private final PersonResolver resolver;
  private final AuthorMatcher fallback;
  private final RelativesPolicy policy;
  @Nullable
  private final Consumer<Explanation> listener;

  /**
   * @param fallback compares the authors no person is known for: the string matcher
   * @param listener sees the explanation of every comparison, for a report; null for none
   */
  public PersonAuthorMatcher(MemoryPersonStore registry, PersonResolver resolver, AuthorMatcher fallback, RelativesPolicy policy,
                             @Nullable Consumer<Explanation> listener) {
    this.registry = registry;
    this.resolver = resolver;
    this.fallback = fallback;
    this.policy = policy;
    this.listener = listener;
  }

  @Override
  public Equality compareTeams(AuthorTeam t1, AuthorTeam t2, AuthorContext ctx, Mode mode) {
    Explanation e = explain(t1, t2, ctx, mode);
    if (listener != null) {
      listener.accept(e);
    }
    return e.verdict();
  }

  /**
   * @return the verdict with what each author resolved to and which rule decided, one decision per author pair compared
   */
  public Explanation explain(AuthorTeam t1, AuthorTeam t2, AuthorContext ctx, Mode mode) {
    if (t1.authors().equals(t2.authors())) {
      var d = new Decision(String.join("|", t1.authors()), String.join("|", t2.authors()), Set.of(), Set.of(), Rule.IDENTICAL,
        Equality.EQUAL);
      return new Explanation(t1, t2, ctx, Equality.EQUAL, List.of(d));
    }
    Integer year1 = PersonResolver.year(t1.year());
    Integer year2 = PersonResolver.year(t2.year());
    List<Decision> decisions = new ArrayList<>();
    Equality team = Equality.DIFFERENT;
    for (String a1 : t1.authors()) {
      Set<Person> p1 = resolver.resolve(a1, ctx.code(), year1, ctx.group());
      for (String a2 : t2.authors()) {
        Set<Person> p2 = resolver.resolve(a2, ctx.code(), year2, ctx.group());
        Decision d = decide(a1, p1, a2, p2, ctx, mode);
        decisions.add(d);
        if (d.verdict() == Equality.EQUAL) {
          return new Explanation(t1, t2, ctx, Equality.EQUAL, decisions);
        }
        if (d.verdict() == Equality.UNKNOWN) {
          team = Equality.UNKNOWN;
        }
      }
    }
    return new Explanation(t1, t2, ctx, team, decisions);
  }

  private Decision decide(String a1, Set<Person> p1, String a2, Set<Person> p2, AuthorContext ctx, Mode mode) {
    if (!p1.isEmpty() && !p2.isEmpty()) {
      if (!Collections.disjoint(p1, p2)) {
        return new Decision(a1, a2, p1, p2, Rule.IDENTITY, Equality.EQUAL);
      }
      if (related(p1, p2)) {
        return new Decision(a1, a2, p1, p2, Rule.RELATIVES, policy.verdict);
      }
      return new Decision(a1, a2, p1, p2, Rule.IDENTITY, Equality.DIFFERENT);
    }
    for (String s1 : forms(a1, p1, ctx)) {
      for (String s2 : forms(a2, p2, ctx)) {
        if (fallback.compareTeams(new AuthorTeam(List.of(s1), null), new AuthorTeam(List.of(s2), null), ctx, mode) == Equality.EQUAL) {
          return new Decision(a1, a2, p1, p2, Rule.FALLBACK, Equality.EQUAL);
        }
      }
    }
    return new Decision(a1, a2, p1, p2, Rule.FALLBACK, Equality.DIFFERENT);
  }

  private boolean related(Set<Person> p1, Set<Person> p2) {
    for (Person a : p1) {
      Set<Person> relatives = registry.relatives(a);
      for (Person b : p2) {
        if (relatives.contains(b)) {
          return true;
        }
      }
    }
    return false;
  }

  /** an unresolved author stands for itself, a resolved one also for every readable form of its persons */
  private Collection<String> forms(String author, Set<Person> persons, AuthorContext ctx) {
    if (persons.isEmpty()) {
      return List.of(author);
    }
    Set<String> forms = new LinkedHashSet<>();
    forms.add(author);
    persons.forEach(p -> registry.keys(p, ctx.code()).stream().filter(PersonAuthorMatcher::readable).forEach(forms::add));
    return forms;
  }

  /**
   * A form the string comparison can read. A comma form such as "gaimard, j p" or one of initials only such as "j p g"
   * parses to a one letter surname, which the comparison takes for the start of any surname beginning with it.
   */
  private static boolean readable(String key) {
    return key.indexOf(',') < 0 && new AuthorshipNormalizer.Author(key).surname.length() > 1;
  }
}
