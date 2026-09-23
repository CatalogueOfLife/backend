package life.catalogue.basgroup;

import life.catalogue.api.model.FormattableName;
import life.catalogue.api.model.HasID;
import life.catalogue.api.vocab.Issue;
import life.catalogue.matching.authorship.AuthorComparator;

import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.NomCode;

import java.util.*;
import java.util.function.BiPredicate;
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
 * <p>
 * Authors often published the same epithet in several genera of one family, so the authorship alone cannot tell
 * Epidendrum mathewsii Rchb.f. from Altensteinia matthewsii Rchb.f. An authorship group holding original names of
 * several genera that a source lists separately is therefore split, and its recombinations only join one of them when
 * the data links them, see {@link #groupBasionyms(NomCode, String, List, Function, Consumer, BiPredicate)}.
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
    return groupBasionyms(code, epithet, names, nameResolver, issueConsumer, (a, b) -> false);
  }

  /**
   * @param code
   * @param epithet
   * @param names names to group. Order does not matter.
   * @param nameResolver function that resolves the original instance into a FormattableName
   * @param issueConsumer
   * @param linked true if the data itself says the two names are homotypic, e.g. by synonymy or an existing name relation.
   *               Only asked for names of an authorship group with original names in several genera,
   *               to decide which of them are the same name and which original the recombinations belong to.
   * @return
   */
  public Collection<HomotypicGroup<T>> groupBasionyms(NomCode code, String epithet, List<T> names,
                                                      Function<T, ? extends FormattableName> nameResolver,
                                                      Consumer<Pair<T, Issue>> issueConsumer,
                                                      BiPredicate<T, T> linked
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
            g.addBasedOn(obj.obj, prio, obj.name.getLabel());
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

    List<HomotypicGroup<T>> result = new ArrayList<>();
    for (var g : groups) {
      result.addAll(splitByOriginalGenus(g, nameResolver, linked));
    }
    return result;
  }

  private static String genusKey(FormattableName name) {
    return name.getGenus() == null ? "" : name.getGenus().toLowerCase();
  }

  /**
   * Splits an authorship group whose original names, i.e. those without a basionym authorship, belong to several genera
   * which a source lists as separate names. An author often published the same epithet in several genera, and those are
   * distinct names, not spelling variants of each other.
   * <p>
   * Original names of different genera are only kept apart with evidence though, as missing brackets make a
   * recombination look like another original name just as often. The original names are partitioned by genus and
   * partitions the data links are merged. Two partitions are distinct if they share a priority, i.e. the same source
   * lists both names separately. Partitions without any such evidence among them are lumped as before,
   * while all partitions of a set that holds distinct ones stay apart.
   * <p>
   * Recombinations of the same genus are the same combination and move together. They join the group of an original
   * name of their own genus, as a rank change, or if the data links one of them to exactly one group, otherwise they form a group of
   * their own without a basionym, as we cannot tell which of the originals they are based on.
   * Based on names are original names too and join the group of their genus, or else follow the recombination rule.
   *
   * @return the group itself if its original names are not split
   */
  private List<HomotypicGroup<T>> splitByOriginalGenus(HomotypicGroup<T> g, Function<T, ? extends FormattableName> nameResolver,
                                                       BiPredicate<T, T> linked
  ) {
    List<T> originals = new ArrayList<>();
    if (g.hasBasionym()) {
      originals.add(g.getBasionym());
    }
    originals.addAll(g.getBasionymVariations());
    Map<String, List<T>> byGenus = new LinkedHashMap<>();
    for (T o : originals) {
      byGenus.computeIfAbsent(genusKey(nameResolver.apply(o)), k -> new ArrayList<>()).add(o);
    }
    if (byGenus.size() < 2) {
      return List.of(g);
    }
    var partitions = partitionOriginals(new ArrayList<>(byGenus.values()), linked);
    if (partitions.size() < 2) {
      return List.of(g);
    }
    LOG.debug("Split homotypic group {} {} into {} original names", g.getEpithet(), g.getAuthorship(), partitions.size());
    // key each partition by the genus of its first name and map all of its genera to that key
    Map<String, List<T>> originalsByGenus = new LinkedHashMap<>();
    Map<String, String> genusPartition = new HashMap<>();
    for (var part : partitions) {
      var key = genusKey(nameResolver.apply(part.get(0)));
      originalsByGenus.put(key, part);
      for (T o : part) {
        genusPartition.put(genusKey(nameResolver.apply(o)), key);
      }
    }

    // one group per partition of original names, keyed by the genus of its first name
    Map<String, HomotypicGroup<T>> split = new LinkedHashMap<>();
    // all members of each split group, the evidence recombinations are compared with
    Map<String, List<T>> members = new HashMap<>();
    for (var e : originalsByGenus.entrySet()) {
      var sg = newGroup(e.getValue().get(0), g, nameResolver);
      for (T o : e.getValue()) {
        sg.add(o, nameResolver.apply(o), priorityFunc.applyAsInt(o));
      }
      split.put(e.getKey(), sg);
      members.put(e.getKey(), new ArrayList<>(e.getValue()));
    }

    // based on names of a genus with original names join that group, the others follow the recombinations
    List<T> basedOn = new ArrayList<>();
    if (g.hasBasedOn()) {
      basedOn.add(g.getBasedOn());
    }
    basedOn.addAll(g.getBasedOnVariations());
    List<T> unplacedBasedOn = new ArrayList<>();
    for (T b : basedOn) {
      var key = genusPartition.get(genusKey(nameResolver.apply(b)));
      if (key != null) {
        split.get(key).addBasedOn(b, priorityFunc.applyAsInt(b), nameResolver.apply(b).getLabel());
        members.get(key).add(b);
      } else {
        unplacedBasedOn.add(b);
      }
    }

    // cluster recombinations by their genus, they are the same combination
    Map<String, List<T>> clusters = new LinkedHashMap<>();
    for (T r : g.getRecombinations()) {
      clusters.computeIfAbsent(genusKey(nameResolver.apply(r)), k -> new ArrayList<>()).add(r);
    }
    for (T b : unplacedBasedOn) {
      clusters.computeIfAbsent(genusKey(nameResolver.apply(b)), k -> new ArrayList<>()).add(b);
    }
    // a combination in the genus of an original name is a rank change of it
    var iter0 = clusters.entrySet().iterator();
    while (iter0.hasNext()) {
      var e = iter0.next();
      var key = genusPartition.get(e.getKey());
      if (key != null) {
        for (T r : e.getValue()) {
          addLinked(split.get(key), r, unplacedBasedOn, nameResolver);
        }
        members.get(key).addAll(e.getValue());
        iter0.remove();
      }
    }
    // attach clusters linked to exactly one group, repeating as an attached cluster can link further ones
    boolean attached = true;
    while (attached) {
      attached = false;
      var iter = clusters.entrySet().iterator();
      while (iter.hasNext()) {
        var cluster = iter.next().getValue();
        String target = null;
        boolean ambiguous = false;
        for (var m : members.entrySet()) {
          if (cluster.stream().anyMatch(r -> m.getValue().stream().anyMatch(o -> linked.test(r, o)))) {
            if (target == null) {
              target = m.getKey();
            } else {
              ambiguous = true;
            }
          }
        }
        if (target != null && !ambiguous) {
          var sg = split.get(target);
          for (T r : cluster) {
            addLinked(sg, r, unplacedBasedOn, nameResolver);
          }
          members.get(target).addAll(cluster);
          iter.remove();
          attached = true;
        }
      }
    }

    List<HomotypicGroup<T>> result = new ArrayList<>(split.values());
    // unlinked clusters stay on their own
    for (var cluster : clusters.values()) {
      var sg = newGroup(cluster.get(0), g, nameResolver);
      for (T r : cluster) {
        addLinked(sg, r, unplacedBasedOn, nameResolver);
      }
      result.add(sg);
    }
    return result;
  }

  /**
   * @param partitions original names partitioned by genus
   * @return the partitions that are distinct names, see {@link #splitByOriginalGenus}
   */
  private List<List<T>> partitionOriginals(List<List<T>> partitions, BiPredicate<T, T> linked) {
    // merge partitions the data links
    List<List<T>> parts = new ArrayList<>();
    for (var p : partitions) {
      List<T> merged = new ArrayList<>(p);
      var iter = parts.iterator();
      while (iter.hasNext()) {
        var other = iter.next();
        if (merged.stream().anyMatch(a -> other.stream().anyMatch(b -> linked.test(a, b) || linked.test(b, a)))) {
          other.addAll(merged);
          merged = other;
          iter.remove();
        }
      }
      parts.add(merged);
    }
    // partitions sharing a priority are listed separately by the same source
    final int n = parts.size();
    boolean[][] distinct = new boolean[n][n];
    for (int i = 0; i < n; i++) {
      Set<Integer> prios = new HashSet<>();
      parts.get(i).forEach(o -> prios.add(priorityFunc.applyAsInt(o)));
      for (int j = i + 1; j < n; j++) {
        distinct[i][j] = distinct[j][i] = parts.get(j).stream().anyMatch(o -> prios.contains(priorityFunc.applyAsInt(o)));
      }
    }
    // lump each set of partitions connected by missing evidence if none of them is distinct from another
    List<List<T>> result = new ArrayList<>();
    boolean[] visited = new boolean[n];
    for (int i = 0; i < n; i++) {
      if (visited[i]) continue;
      List<Integer> comp = new ArrayList<>();
      Deque<Integer> queue = new ArrayDeque<>(List.of(i));
      visited[i] = true;
      while (!queue.isEmpty()) {
        int x = queue.poll();
        comp.add(x);
        for (int y = 0; y < n; y++) {
          if (!visited[y] && !distinct[x][y]) {
            visited[y] = true;
            queue.add(y);
          }
        }
      }
      boolean anyDistinct = comp.stream().anyMatch(x -> comp.stream().anyMatch(y -> distinct[x][y]));
      if (anyDistinct) {
        comp.forEach(x -> result.add(parts.get(x)));
      } else {
        List<T> lumped = new ArrayList<>();
        comp.forEach(x -> lumped.addAll(parts.get(x)));
        result.add(lumped);
      }
    }
    return result;
  }

  private HomotypicGroup<T> newGroup(T primary, HomotypicGroup<T> g, Function<T, ? extends FormattableName> nameResolver) {
    return new HomotypicGroup<>(primary, g.getEpithet(), nameResolver.apply(primary).getBasionymOrCombinationAuthorship(), g.getCode());
  }

  /**
   * Adds a recombination, or a based on name that had no group of its own genus, keeping its role.
   */
  private void addLinked(HomotypicGroup<T> g, T obj, List<T> basedOn, Function<T, ? extends FormattableName> nameResolver) {
    if (basedOn.contains(obj)) {
      g.addBasedOn(obj, priorityFunc.applyAsInt(obj), nameResolver.apply(obj).getLabel());
    } else {
      g.addRecombination(obj);
    }
  }

}
