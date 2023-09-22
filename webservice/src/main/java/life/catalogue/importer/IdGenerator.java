package life.catalogue.importer;

import life.catalogue.common.id.IdConverter;
import life.catalogue.common.text.StringUtils;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generator for string identifiers using backed by an integer sequence.
 * Identifiers can have a shared prefix and are based on pure case sensitive latin alphanumerical characters.
 */
public class IdGenerator {
  private static final String AVAIL_PREFIX_CHARS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
  private static final char PREFIX_PAD_CHAR = 'x';
  
  private final Supplier<Integer> intIdSupplier;
  private final IdConverter idConverter = IdConverter.LATIN29;
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
  public IdGenerator(Stream<String> existingIds) {
    this(smallestNonExistingPrefix(existingIds), 0);
  }

  public IdGenerator(String preferredPrefix, Stream<String> existingIds, int start) {
    this(smallestNonExistingPrefix(preferredPrefix, existingIds), start);
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
    return smallestNonExistingPrefix(String.valueOf(PREFIX_PAD_CHAR), existingIds);
  }

  private static String smallestNonExistingPrefix(String preferredPrefix, Stream<String> existingIds) {
    final StringBuilder prefix = new StringBuilder(preferredPrefix);

    Set<String> ids = existingIds
            .filter(Objects::nonNull)
            .filter(s -> s.startsWith(prefix.toString()))
            .collect(Collectors.toSet());
    while (!ids.isEmpty()) {
      Set<Character> idchars = StringUtils.charSet(AVAIL_PREFIX_CHARS);
      for (String id : ids) {
        if (id.length() < 1) continue;
        idchars.remove(id.charAt(0));
      }
      if (idchars.isEmpty()) {
        prefix.append(PREFIX_PAD_CHAR);
      } else {
        if (idchars.contains(PREFIX_PAD_CHAR)) {
          prefix.append(PREFIX_PAD_CHAR);
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
  
  public IdGenerator setPrefix(String prefPrefix, Stream<String> existingIds) {
    this.prefix = smallestNonExistingPrefix(prefPrefix, existingIds);
    return this;
  }
  
  public String id(int key) {
    return prefix + idConverter.encode(key);
  }
  
  public String next() {
    return id(intIdSupplier.get());
  }

  /**
   * @return the integer representation of the given id
   */
  public int decode(String id) {
    if (!id.startsWith(prefix)) {
      throw new IllegalArgumentException("ID " +id+ " has no prefix " + prefix);
    }
    return idConverter.decode(id.substring(prefix.length()));
  }
}
