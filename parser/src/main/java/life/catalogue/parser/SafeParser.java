package life.catalogue.parser;

import life.catalogue.api.model.IssueContainer;
import life.catalogue.api.vocab.Issue;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * A parsing utility class wrapping a Parser<T> instance so that no UnparsableException is thrown.
 * It offers accesors to the parsing result similar to the Optional class.
 */
@SuppressWarnings("ALL")
public class SafeParser<T> {
  private final Optional<? extends T> result;

  public static <T> SafeParser<T> parse(Parser<T> parser, String value) {
    return new SafeParser<T>(value, parser);
  }

  private SafeParser(String value, Parser<T> parser) {
    Optional<? extends T> result = null;
    try {
      result = parser.parse(value);
    } catch (UnparsableException e) {
      // result is null now
    }
    this.result = result;
  }

  /**
   * The value could be parsed and was not empty.
   */
  public boolean isPresent() {
    return result != null && result.isPresent();
  }

  /**
   * If false, the value could not be parsed and the parser threw an UnparsableException.
   */
  public boolean isParsable() {
    return result != null;
  }

  /**
   * The value was considered empty, e.g. pure whitespace
   */
  public boolean isEmpty() {
    return result != null && !result.isPresent();
  }
  
  /**
   * Returns a value and throws if it is not present.
   * Make sure it is present before calling this!
   */
  public T get() {
    return result.get();
  }

  /**
   * Always returns a value, if needed falling back to a default.
   *
   * @return the parsed value if present, other if empty or unparsable
   */
  public T orElse(T other) {
    return isPresent() ? result.get() : other;
  }

  /**
   * Always returns a value, if needed falling back to defaults for empty or unparsable.
   */
  public T orElse(T empty, T unparsable) {
    return isPresent() ? result.get() : (isParsable() ? unparsable : empty);
  }

  /**
   * Always returns a value, if needed falling back to null.
   *
   * @return the parsed value if present, otherwise null
   */
  public T orNull() {
    return isPresent() ? result.get() : null;
  }

  /**
   * Always returns a value, if needed falling back to null.
   * If the value was unparsable an issue is added to the issue collector.
   *
   * @return the parsed value if present, null if empty or unparsable
   */
  public T orNull(Issue unparsableIssue, IssueContainer issues) {
    return orElse(() -> null, unparsableIssue, issues);
  }

  /**
   * Always returns a value, if needed falling back to a default.
   * If the value was unparsable an issue is added to the issue collector.
   *
   * @return the parsed value if present, other if empty or unparsable
   */
  public T orElse(T other, Issue unparsableIssue, IssueContainer issues) {
    return orElse(()->other, unparsableIssue, issues);
  }

  /**
   * Same as orElse but using the supplier syntax
   */
  public T orElse(Supplier<? extends T> other, Issue unparsableIssue, IssueContainer issues) {
    if (isParsable()) {
      if (result.isPresent()) {
        return result.get();
      }
    } else {
      issues.add(unparsableIssue);
    }
    return other.get();
  }
}
