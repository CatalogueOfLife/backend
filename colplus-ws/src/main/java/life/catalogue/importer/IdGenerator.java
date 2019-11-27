package life.catalogue.importer;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import life.catalogue.common.id.IdConverter;
import life.catalogue.common.text.StringUtils;

/**
 * Generator for string identifiers using backed by an integer sequence.
 * Identifiers can have a shared prefix and are based on pure case sensitive latin alphanumerical characters.
 */
public class IdGenerator {
  private static final String availChars = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
  private static final char PREFERRED_PREFIX = 'x';
  
  private final Supplier<Integer> intIdSupplier;
  private final IdConverter idConverter = IdConverter.LATIN32;
  private String prefix;
  
  public IdGenerator() {
    this("", 0);
  }
  
  public IdGenerator(String prefix) {
    this(prefix, 0);
  }
  
  public IdGenerator(String prefix, int start) {
    this.prefix = prefix;
    intIdSupplier = new AtomicInteger(start)::incrementAndGet;
  }
  
  /**
   * Uses a shared counter with no prefix
   */
  public IdGenerator(Supplier<Integer> intIdSupplier) {
    this("", intIdSupplier);
  }
  
  /**
   * Uses a shared counter with a custom prefix
   */
  public IdGenerator(String prefix, Supplier<Integer> intIdSupplier) {
    this.prefix = prefix;
    this.intIdSupplier = intIdSupplier;
  }
  
  private static String smallestNonExistingPrefix(Stream<String> existingIds) {
    final char preferredPrefixChar = PREFERRED_PREFIX;
    final StringBuilder prefix = new StringBuilder(String.valueOf(preferredPrefixChar));
    Set<String> ids = existingIds.filter(s -> s.startsWith(prefix.toString())).collect(Collectors.toSet());
    while (!ids.isEmpty()) {
      Set<Character> idchars = StringUtils.charSet(availChars);
      for (String id : ids) {
        if (id == null || id.length() < 1) continue;
        idchars.remove(id.charAt(0));
      }
      if (idchars.isEmpty()) {
        prefix.append(preferredPrefixChar);
      } else {
        if (idchars.contains(preferredPrefixChar)) {
          prefix.append(preferredPrefixChar);
        } else {
          prefix.append(idchars.iterator().next());
        }
      }
      ids.removeIf(s -> !s.startsWith(prefix.toString()));
    }
    return prefix.toString();
  }
  
  public String getPrefix() {
    return prefix;
  }
  
  public IdGenerator setPrefix(Stream<String> existingIds) {
    this.prefix = smallestNonExistingPrefix(existingIds);
    return this;
  }
  
  public String id(int key) {
    return prefix + idConverter.encode(key);
  }
  
  public String next() {
    return id(intIdSupplier.get());
  }
  
}
