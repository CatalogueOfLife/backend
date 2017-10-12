package org.col.parser;

import org.col.api.vocab.NameType; /**
 *
 */
public class UnparsableException extends IllegalArgumentException {

  public UnparsableException(Throwable e) {
    super(e);
  }

  public UnparsableException(NameType type, String name) {
    super("Unparsable "+type+" name: " + name);
  }
}
