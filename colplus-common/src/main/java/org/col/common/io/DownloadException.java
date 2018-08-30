package org.col.common.io;

import java.io.IOException;
import java.net.URI;

public class DownloadException extends IOException {

  private final URI uri;

  public DownloadException(URI uri, String message) {
    super(message(uri, message));
    this.uri = uri;
  }

  public DownloadException(URI uri, Throwable cause) {
    super(message(uri, cause.toString()), cause);
    this.uri = uri;
  }

  private static String message(URI uri, String msg) {
    return "Download of " + uri + " failed. " + msg;
  }

  @Override
  public String getMessage() {
    return super.getMessage();
  }

  public URI getUri() {
    return uri;
  }
}
