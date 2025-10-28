package life.catalogue.dw.jersey.filter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.ws.rs.core.UriInfo;

public class FilterUtils {
  private static final Pattern DATASET_PATH  = Pattern.compile("dataset/(\\d+)");

  private FilterUtils() {
  }

  static int datasetKey(UriInfo uri) {
    Matcher m = DATASET_PATH.matcher(uri.getPath());
    if (m.find()) {
      // parsing cannot fail, we have a pattern
      return Integer.parseInt(m.group(1));
    }
    throw new IllegalArgumentException("No dataset key found in " + uri.getPath());
  }

  static Integer datasetKeyOrNull(UriInfo uri) {
    Matcher m = DATASET_PATH.matcher(uri.getPath());
    if (m.find()) {
      // parsing cannot fail, we have a pattern
      return Integer.parseInt(m.group(1));
    }
    return null;
  }
}
