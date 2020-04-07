package life.catalogue.parser;

import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.*;

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
  public void parse() {
    for (Parser<Integer> p : INT_PARSERS) {
      SafeParser sp = SafeParser.parse(p, "something");
      Optional<Integer> opt = sp.getOptional();
      assertNotNull(opt);
      assertNotNull(sp.orElse(1));
    }
  }

  @Test
  public void getOptional() {
  }
}