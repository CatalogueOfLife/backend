package life.catalogue.parser;

import life.catalogue.api.vocab.area.Area;
import life.catalogue.api.vocab.area.Country;
import life.catalogue.api.vocab.area.Gazetteer;
import life.catalogue.api.vocab.area.GenericArea;
import life.catalogue.common.text.CSVUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves an English label for an area id according to a known gazetteer.
 *
 * ISO is served by the {@link Country} vocabulary bundled in the api module.
 * All other gazetteers are loaded once at construction from a configurable on-disk gazetteer
 * directory laid out as:
 * <pre>
 *   &lt;gazetteerDir&gt;/&lt;gazetteer-prefix&gt;/labels.tsv     # id&lt;TAB&gt;english-name
 *   &lt;gazetteerDir&gt;/&lt;gazetteer-prefix&gt;/features/&lt;id&gt;.geojson
 * </pre>
 * Unknown ids fall back to the id itself, matching the previous behavior.
 */
public class AreaLabelLookup {
  private static final Logger LOG = LoggerFactory.getLogger(AreaLabelLookup.class);

  private final Map<Gazetteer, Map<String, String>> labels;

  public AreaLabelLookup() {
    this(null);
  }

  public AreaLabelLookup(@Nullable File gazetteerDir) {
    Map<Gazetteer, Map<String, String>> all = new EnumMap<>(Gazetteer.class);
    if (gazetteerDir != null) {
      if (!gazetteerDir.isDirectory()) {
        LOG.warn("Configured gazetteer dir {} does not exist", gazetteerDir);
      } else {
        for (Gazetteer g : Gazetteer.values()) {
          if (g == Gazetteer.ISO) continue;
          Map<String, String> m = loadLabels(gazetteerDir, g);
          if (m != null) {
            all.put(g, m);
          }
        }
      }
    }
    this.labels = Map.copyOf(all);
  }

  private static Map<String, String> loadLabels(File dir, Gazetteer g) {
    File f = new File(new File(dir, g.prefix()), "labels.tsv");
    if (!f.isFile()) {
      LOG.warn("No {} labels file at {}", g, f);
      return null;
    }
    Map<String, String> map = new HashMap<>();
    try (InputStream in = new FileInputStream(f);
         var rows = CSVUtils.parse(in, 0, '\t')) {
      rows.forEach(row -> {
        if (row.size() >= 2 && row.get(0) != null && row.get(1) != null) {
          map.put(row.get(0), row.get(1));
        }
      });
    } catch (IOException e) {
      LOG.error("Failed to read {} labels from {}", g, f, e);
      return null;
    }
    LOG.info("Loaded {} {} labels from {}", map.size(), g, f);
    return Map.copyOf(map);
  }

  public String findLabel(Gazetteer gazetteer, String id) {
    if (id == null || gazetteer == null) return null;
    try {
      Area areaVoc = switch (gazetteer) {
        case ISO -> Country.fromIsoCode(id).orElse(null);
        default -> null;
      };
      if (areaVoc != null) {
        return areaVoc.getName();
      }
    } catch (IllegalArgumentException e) {
      // unknown bundled id — fall through
    }
    Map<String, String> map = labels.get(gazetteer);
    if (map != null) {
      String name = map.get(id);
      if (name != null) return name;
    }
    return id;
  }
}
