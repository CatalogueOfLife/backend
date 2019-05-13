package org.col.importer;

import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.col.common.text.StringUtils;
import org.hashids.Hashids;

public class IdGenerator {
  public static final IdGenerator NAME_INDEX_IDS = new IdGenerator("NI");
  public static final IdGenerator COL_INDEX_IDS = new IdGenerator();
  
  private static final String availChars = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
  private static final char PREFERRED_PREFIX = 'x';
  
  private final AtomicLong counter;
  private final Hashids hashids = new Hashids("dvr4GgTx", 4, availChars);
  private String prefix;
  
  public IdGenerator() {
    this("", 0);
  }
  
  public IdGenerator(String prefix) {
    this(prefix, 0);
  }
  
  public IdGenerator(String prefix, long start) {
    this.prefix = prefix;
    counter = new AtomicLong(start);
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
  
  public long getCounter() {
    return counter.get();
  }
  
  public void setCounter(long id) {
    counter.set(id);
  }

  public String getPrefix() {
    return prefix;
  }
  
  public IdGenerator setPrefix(Stream<String> existingIds) {
    this.prefix = smallestNonExistingPrefix(existingIds);
    return this;
  }
  
  public String id(long key) {
    return prefix + hashids.encode(key);
  }
  public String next() {
    return id(counter.incrementAndGet());
  }
}
