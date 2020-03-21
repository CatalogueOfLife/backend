package life.catalogue.common.io;

import java.net.URI;

public class UriUtils {

  private UriUtils(){}

  public static boolean isFile(URI uri) {
    return (uri.getScheme() != null && (uri.getScheme().equals("content") || uri.getScheme().equals("file")));
  }

}
