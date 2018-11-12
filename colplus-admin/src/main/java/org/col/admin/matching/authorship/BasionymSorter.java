package org.col.admin.matching.authorship;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

import com.google.common.base.Functions;
import com.google.common.collect.Lists;
import org.col.api.model.Name;
import org.gbif.nameparser.api.Authorship;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A utility to sort a list of parsed names into sets sharing the same basionym judging only the authorship not epithets.
 * A name without any authorship at all will be ignored and not returned in any group.
 */
public class BasionymSorter {
  private static final Logger LOG = LoggerFactory.getLogger(BasionymSorter.class);
  private AuthorComparator authorComp;
  
  public BasionymSorter() {
    this.authorComp = AuthorComparator.createWithAuthormap();
  }
  
  public BasionymSorter(AuthorComparator authorComp) {
    this.authorComp = authorComp;
  }
  
  public static class MultipleBasionymException extends Exception {
  
  }
  
  public Collection<BasionymGroup<Name>> groupBasionyms(Iterable<Name> names) {
    return groupBasionyms(names, Functions.<Name>identity());
  }
  
  private <T> BasionymGroup<T> findExistingGroup(T p, List<BasionymGroup<T>> groups, Function<T, Name> func) {
    Name pn = func.apply(p);
    for (BasionymGroup<T> g : groups) {
      Name representative = func.apply(g.getRecombinations().get(0));
      if (authorComp.compareStrict(pn.getBasionymAuthorship(), representative.getBasionymAuthorship())) {
        return g;
      }
    }
    return null;
  }
  
  private <T> T findBasionym(Authorship authorship, List<T> originals, Function<T, Name> func) throws MultipleBasionymException {
    List<T> basionyms = Lists.newArrayList();
    for (T obj : originals) {
      Name b = func.apply(obj);
      if (authorComp.compareStrict(authorship, b.getCombinationAuthorship())) {
        basionyms.add(obj);
      }
    }
    if (basionyms.isEmpty()) {
      // try again without year in case we didnt find any but make sure we only match once!
      if (authorship != null) {
        Authorship aNoYear = copyWithoutYear(authorship);
        for (T obj : originals) {
          Name b = func.apply(obj);
          if (authorComp.compareStrict(aNoYear, copyWithoutYear(b.getCombinationAuthorship()))) {
            basionyms.add(obj);
          }
        }
      }
    }
    
    // we have more than one match, dont use it!
    if (basionyms.size() == 1) {
      return basionyms.get(0);
    } else if (basionyms.isEmpty()) {
      return null;
    }
    
    throw new MultipleBasionymException();
  }
  
  private static Authorship copyWithoutYear(Authorship a) {
    Authorship a2 = new Authorship();
    a2.setAuthors(a.getAuthors());
    a2.setExAuthors(a.getExAuthors());
    return a2;
  }
  
  /**
   * Grouping that allows to use any custom class as long as there is a function that returns a Name instance.
   * The list of groups returned only contains groups with no or one known basionym. Any uncertain cases like groups with multiple basionyms are excluded!
   */
  public <T> Collection<BasionymGroup<T>> groupBasionyms(Iterable<T> names, Function<T, Name> func) {
    List<BasionymGroup<T>> groups = Lists.newArrayList();
    // first split names into recombinations and original names not having a basionym authorship
    // note that we drop any name without authorship here!
    List<T> recombinations = Lists.newArrayList();
    List<T> originals = Lists.newArrayList();
    for (T obj : names) {
      Name p = func.apply(obj);
      if (p != null) {
        if (!p.getBasionymAuthorship().isEmpty()) {
          recombinations.add(obj);
        } else if (!p.getCombinationAuthorship().isEmpty()) {
          originals.add(obj);
        }
      } else {
        LOG.warn("No parsed name returned for name object {}", obj);
      }
    }
    
    // now group the recombinations
    for (T recomb : recombinations) {
      BasionymGroup<T> group = findExistingGroup(recomb, groups, func);
      // create new group if needed
      if (group == null) {
        Name pn = func.apply(recomb);
        if (pn != null) {
          group = new BasionymGroup<T>();
          group.setName(pn.getTerminalEpithet(), pn.getBasionymAuthorship());
          groups.add(group);
          group.getRecombinations().add(recomb);
        } else {
          LOG.warn("No parsed name returned for name recombination {}", recomb);
        }
      } else {
        group.getRecombinations().add(recomb);
      }
    }
    // finally try to find the basionym for each group in the list of original names
    Iterator<BasionymGroup<T>> iter = groups.iterator();
    while (iter.hasNext()) {
      BasionymGroup<T> group = iter.next();
      try {
        group.setBasionym(findBasionym(group.getAuthorship(), originals, func));
      } catch (MultipleBasionymException e) {
        LOG.info("Ignore group with multiple basionyms found for {} {} in {} original names", group.getEpithet(), group.getAuthorship(), originals.size());
        iter.remove();
      }
    }
    return groups;
  }
}
