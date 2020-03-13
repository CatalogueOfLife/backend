package life.catalogue.parser;


import org.gbif.nameparser.api.NameType;

/**
 *
 */
public class UnparsableException extends Exception {
  
  public UnparsableException(Throwable e) {
    super(e);
  }
  
  public UnparsableException(String msg) {
    super(msg);
  }
  
  public UnparsableException(String msg, Throwable e) {
    super(msg, e);
  }
  
  public UnparsableException(Class clazz, String value) {
    super(msg(clazz, value));
  }

  public UnparsableException(Class clazz, String value, String message) {
    super(msg(clazz, value) + ". " + message);
  }

  private static String msg(Class clazz, String value) {
    return "Failed to parse >" + value + "< into " + clazz.getSimpleName();
  }

  /**
   * Convenience constructor for unparsable names.
   */
  public UnparsableException(NameType type, String name) {
    super("Unparsable " + type + " name: " + name);
  }
}
