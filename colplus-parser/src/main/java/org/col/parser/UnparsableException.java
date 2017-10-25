package org.col.parser;

import org.col.api.vocab.NameType; /**
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
    super("Failed to parse >"+value+"< into "+clazz.getSimpleName());
  }

  /**
   * Convenience constructor for unparsable names.
   */
  public UnparsableException(NameType type, String name) {
    super("Unparsable "+type+" name: " + name);
  }
}
