package org.col.parser.gna;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import scala.Option;
import scala.Some;
import scala.collection.JavaConversions;

import java.util.List;
import java.util.Map;

/**
 *
 */
public class Authorship {
    private final Map<String, Object> combination;
    private final Map<String, Object> basionym;

    private static Map<String, Object> someMap(Object obj) {
        if (obj instanceof Some) {
            return JavaConversions.mapAsJavaMap(((Some<scala.collection.Map>) obj).get());
        } else if (obj instanceof scala.collection.Map) {
            return JavaConversions.mapAsJavaMap((scala.collection.Map) obj);
        }
        return Maps.newHashMap();
    }

    /**
     * Lazily initializes the authorship maps when needed.
     * This needs to be called manually before any authorship getters
     */
    Authorship(Map<String, Object> map) {
        if (map.containsKey("authorship")) {
            Map<String, Object> auth = someMap(map.get("authorship"));
            Map<String, Object> comb = someMap(auth.get("combination_authorship"));
            Map<String, Object> bas  = someMap(auth.get("basionym_authorship"));
            // in case of just a combination author it comes as the basionym author, swap!
            if (comb.isEmpty() && !bas.isEmpty() && !((String)auth.get("value")).startsWith("(")) {
                combination = bas;
                basionym = comb;
            } else {
                combination = comb;
                basionym = bas;
            }
        } else {
            combination = Maps.newHashMap();
            basionym = Maps.newHashMap();
        }
    }

    public List<String> getCombinationAuthors() {
        return authors(combination, false);
    }

    public List<String> getBasionymAuthors() {
        return authors(basionym, false);
    }

    public String getCombinationYear() {
        return mapValue(combination.get("year"));
    }

    public String getBasionymYear() {
        return mapValue(basionym.get("year"));
    }

    private static List<String> authors(Map<String, Object> auth, boolean ex) {
        String key = ex ? "ex_authors" : "authors";
        if (auth.containsKey(key)) {
          return JavaConversions.seqAsJavaList( (scala.collection.immutable.List) auth.get(key));
        }
        return Lists.newArrayList();
    }

    private static String mapValue(Object val) {
        if (val == null || val instanceof Option) {
            return null;
        }
        return (String) JavaConversions.mapAsJavaMap((scala.collection.Map) val).get("value");
    }
}
