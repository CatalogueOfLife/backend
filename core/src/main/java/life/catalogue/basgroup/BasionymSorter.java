package life.catalogue.basgroup;

import life.catalogue.api.model.FormattableName;
import life.catalogue.api.model.HasID;
import life.catalogue.api.vocab.Issue;
import life.catalogue.matching.authorship.AuthorComparator;

import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.NomCode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToIntFunction;

import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.unimi.dsi.fastutil.Pair;

/**
 * A utility to sort a collection of parsed names into sets sharing the same basionym judging only the authorship NOT epithets.
 * A name without any authorship at all will be ignored and not returned in any group.
 */
public class BasionymSorter<T extends HasID<String>> {
  private static final Logger LOG = LoggerFactory.getLogger(BasionymSorter.class);
  private final AuthorComparator authorComp;
  private final ToIntFunction<T> priorityFunc;

  /**
   *
   * @param authorComp
   * @param priorityFunc function that retrieves the (merging) priority for the given name. The less the more priority
   * @return
   */
  public BasionymSorter(AuthorComparator authorComp, ToIntFunction<T> priorityFunc) {
    this.authorComp = authorComp;
    this.priorityFunc = priorityFunc;
  }

  private static class TNamePair<T> implements Comparable<TNamePair<T>> {
    final T obj;
    final FormattableName name;

    private TNamePair(T obj, FormattableName name) {
      this.obj = obj;
      this.name = name;
    }

    /**
     * names with ex authors first, then recombinations, then original and last names without authors
     */
    @Override
    public int compareTo(@NotNull TNamePair<T> o) {
      var a1 = name.getBasionymOrCombinationAuthorship();
      var a2 = o.name.getBasionymOrCombinationAuthorship();

      if (a1.isEmpty() && a2.isEmpty()) return 0;
      if (a1.isEmpty()) return 2;
      if (a2.isEmpty()) return -2;

      int comp = Boolean.compare(!a1.hasExAuthors(), !a2.hasExAuthors());
      if (comp != 0) return comp;
      return Boolean.compare(!name.hasBasionymAuthorship(), !o.name.hasBasionymAuthorship());
    }

    public String toString() {
      return name.toString();
    }
  }

  /**
   * @param code
   * @param epithet
   * @param names names to group. Order does not matter.
   * @param nameResolver function that resolves the original instance into a FormattableName
   * @param issueConsumer
   * @return
   */
  public Collection<HomotypicGroup<T>> groupBasionyms(NomCode code, String epithet, List<T> names,
                                                      Function<T, ? extends FormattableName> nameResolver,
                                                      Consumer<Pair<T, Issue>> issueConsumer
  ) {
    List<HomotypicGroup<T>> groups = new ArrayList<>();

    List<TNamePair<T>> fNames = new ArrayList<>();
    for (T obj : names) {
      fNames.add(new TNamePair(obj, nameResolver.apply(obj)));
    }
    Collections.sort(fNames);

    for (TNamePair<T> obj : fNames) {
      int prio = priorityFunc.applyAsInt(obj.obj);
      if (obj.name != null && obj.name.hasAuthorship()) {
        Authorship cand = obj.name.getBasionymOrCombinationAuthorship();
        boolean create = true;
        for (HomotypicGroup<T> g : groups) {
          Authorship gex = g.getAuthorship().hasExAuthors() ? new Authorship(g.getAuthorship().getExAuthors(), g.getAuthorship().getAuthors(), g.getAuthorship().getYear()) : null;
          // this ignores the ex author
          if (authorComp.compareStrict(cand, g.getAuthorship(), code, 5)) {
            g.add(obj.obj, obj.name, prio);
            create = false;
            break;
          } else if (gex != null && authorComp.compareStrict(cand, gex, code, 2)) {
            // homotypic based on relations
            g.addBasedOn(obj.obj, prio);
            create = false;
            break;
          }
        }
        if (create) {
          var g = new HomotypicGroup<T>(obj.obj, epithet, cand, code);
          g.add(obj.obj, obj.name, prio);
          groups.add(g);
        }

      } else {
        LOG.warn("No parsed name returned for name object {}", obj);
      }
    }
    return groups;
  }

}
