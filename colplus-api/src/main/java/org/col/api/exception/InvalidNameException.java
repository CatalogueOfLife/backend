package org.col.api.exception;

import org.col.api.Name;

/**
 * Exception to throw when a name has inconsistent property states.
 * For example when the rank does not match the given epithets.
 */
public class InvalidNameException extends IllegalStateException {
  private final Name name;

  public InvalidNameException(String msg, Name name) {
    super(msg);
    this.name = name;
  }

  public InvalidNameException(Name name) {
    this.name = name;
  }

  public Name getName() {
    return name;
  }
}
