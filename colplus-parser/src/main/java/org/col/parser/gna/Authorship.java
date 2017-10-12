package org.col.parser.gna;

import com.google.common.collect.Lists;
import scala.Option;
import scala.collection.JavaConversions;
import scala.collection.Map;

import java.util.List;

/**
 *
 */
public class Authorship {
  private final String authorship;
  private final Map<String, Object> combination;
  private final Map<String, Object> basionym;


  /**
   * Lazily initializes the authorship maps when needed.
   * This needs to be called manually before any authorship getters
   */
  Authorship(Map<String, Object> authorshipMap) {
    authorship = (String) authorshipMap.get("value").get();
    Map<String, Object> comb = ScalaUtils.optionMap(authorshipMap.get("combination_authorship"));
    Map<String, Object> bas = ScalaUtils.optionMap(authorshipMap.get("basionym_authorship"));
    // in case of just a combination author it comes as the basionym author, swap!
    if (comb.isEmpty() && !bas.isEmpty() && !authorship.startsWith("(")) {
      combination = bas;
      basionym = comb;
    } else {
      combination = comb;
      basionym = bas;
    }
  }

  /**
   * @return the full authorship string
   */
  public String getAuthorship() {
    return authorship;
  }

  public List<String> getCombinationAuthors() {
    return authors(combination, false);
  }

  public List<String> getBasionymAuthors() {
    return authors(basionym, false);
  }

  public String getCombinationYear() {
    return mapValueString(combination, "year");
  }

  public String getBasionymYear() {
    return mapValueString(basionym,"year");
  }

  private static List<String> authors(Map<String, Object> auth, boolean ex) {
    String key = ex ? "ex_authors" : "authors";
    if (auth.contains(key)) {
      return JavaConversions.seqAsJavaList((scala.collection.immutable.List) auth.get(key).get());
    }
    return Lists.newArrayList();
  }

  /**
   * Return the nested map value for the key and use "value" as key for the second,nested map.
   */
  private static String mapValueString(Map map, String key) {
    Option val = ScalaUtils.unwrap(map.get(key));
    if (val.isDefined()) {
      return ScalaUtils.mapString((Map)val.get(), "value");
    }
    return null;
  }
}
