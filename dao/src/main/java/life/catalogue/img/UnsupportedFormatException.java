package life.catalogue.img;

/**
 * Media format not supported by the server.
 */
public class UnsupportedFormatException extends IllegalArgumentException {
  
  public UnsupportedFormatException() {
  }
  
  public UnsupportedFormatException(String message) {
    super(message);
  }
  
  public UnsupportedFormatException(String message, Throwable cause) {
    super(message, cause);
  }
}
