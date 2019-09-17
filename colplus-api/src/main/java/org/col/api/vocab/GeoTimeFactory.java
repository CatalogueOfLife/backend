package org.col.api.vocab;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.col.api.util.JsonLdReader;
import org.col.common.io.Resources;
import org.col.common.text.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GeoTimeFactory {
  
  private static final Logger LOG = LoggerFactory.getLogger(GeoTimeFactory.class);
  private static String ISC_FILE = "vocab/geotime/GeoSciML-isc2017.json";
  // "younger bound -315.2 +|-0.2 Ma"
  private static Pattern BOUNDS = Pattern.compile("(young|old)(?:er)? +bound +-?([0-9.]+) [^a-z]+Ma", Pattern.CASE_INSENSITIVE);
  private static Pattern PREFIX = Pattern.compile("^[a-z]{1,4}:");
  
  public static Stream<JsonLdReader.LDItem> readJsonLD() {
    try {
      InputStream stream = Resources.stream(ISC_FILE);
      JsonLdReader.JsonLD json = JsonLdReader.read(stream);
      return json.graph.stream()
          .filter(item ->
              item.type.contains("gts:GeochronologicEra")
           && item.isReplacedBy == null
          );
    } catch (IOException e) {
      LOG.error("Failed to read geotime JsonLD", e);
      throw new RuntimeException(e);
    }
  }
  
  static Map<String, GeoTime> readFile() {
    Map<String, String> hasParent = new HashMap<>();
    List<GeoTime> times = new ArrayList<>();

    readJsonLD().forEach(item -> {
        final String name = removePrefix(item.id);
        //final String name = findEnLabel(item.prefLabel);
        if (item.broader != null) {
          hasParent.put(name, removePrefix(item.broader));
        }
        Double[] bounds = findBounds(item);
        GeoTime gt = new GeoTime(
            name,
            findUnit(item.type),
            removePrefix(item.inScheme),
            bounds[0],
            bounds[1],
            null
        );
        times.add(gt);
    });

    Map<String, GeoTime> map = new HashMap<>();
    // sorts by unit, then time
    Collections.sort(times);
    for (GeoTime src : times) {
      GeoTime parent = null;
      if (hasParent.containsKey(src.getName())) {
        parent = map.get(norm(hasParent.get(src.getName())));
        if (parent == null) {
          LOG.warn("GeoTime {} with unknown parent {}", src, hasParent.get(src.getName()));
        }
      }
      map.put(norm(src.getName()), new GeoTime(src, parent));
    }
    return map;
  }
  
  private static Double[] findBounds(JsonLdReader.LDItem item) {
    Double[] bounds = new Double[2];
    if (item.comment != null) {
      for (JsonLdReader.Label com : item.comment) {
        Matcher m = BOUNDS.matcher(com.value);
        if (m.find()) {
          int idx = m.group(1).startsWith("y") ? 1 : 0;
          Double ma = Double.parseDouble(m.group(2));
          bounds[idx] = ma;
        }
      }
    }
    return bounds;
  }
  
  public static String removePrefix(String value) {
    return value == null ? null : PREFIX.matcher(value).replaceFirst("");
  }
  
  private static GeoUnit findUnit(List<String> types) {
    for (String t : types) {
      t = removePrefix(t).toUpperCase().replaceAll("-", "");
      try {
        return GeoUnit.valueOf(t);
      } catch (IllegalArgumentException e) {
        // ignore, many other types included in this list
      }
    }
    LOG.debug("No geotime unit found in types {}", types);
    return null;
  }
  
  private static String findEnLabel(List<JsonLdReader.Label> labels) {
    if (labels != null) {
      for (JsonLdReader.Label l : labels) {
        if (l.language.equalsIgnoreCase("en")) {
          return l.value;
        }
      }
    }
    LOG.debug("No english label found for labels {}", labels);
    return null;
  }
  
  private static String norm(String x) {
    return StringUtils.foldToAscii(x).trim().toUpperCase();
  }
  
}
