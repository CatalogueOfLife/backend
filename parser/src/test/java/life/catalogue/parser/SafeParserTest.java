package life.catalogue.parser;

import java.util.List;
import java.util.Optional;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class SafeParserTest {

  static class FailingParser implements Parser<Integer> {
    @Override
    public Optional<Integer> parse(String value) throws UnparsableException {
      throw new UnparsableException("Cant parse "+value);
    }
  }
  static class NullParser implements Parser<Integer> {
    @Override
    public Optional<Integer> parse(String value) throws UnparsableException {
      return Optional.ofNullable(null);
    }
  }
  static class EmptyParser implements Parser<Integer> {
    @Override
    public Optional<Integer> parse(String value) throws UnparsableException {
      return Optional.empty();
    }
  }
  static class OneParser implements Parser<Integer> {
    @Override
    public Optional<Integer> parse(String value) throws UnparsableException {
      return Optional.of(1);
    }
  }

  static List<Parser<Integer>> INT_PARSERS = List.of(new FailingParser(), new NullParser(), new EmptyParser(), new OneParser());

  @Test
  public void orElse() {
    for (Parser<Integer> p : INT_PARSERS) {
      SafeParser sp = SafeParser.parse(p, "something");
      assertNotNull(sp.orElse(1));
    }
  }

}