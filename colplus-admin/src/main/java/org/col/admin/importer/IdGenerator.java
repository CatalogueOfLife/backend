package org.col.admin.importer;

import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.col.common.text.StringUtils;
import org.hashids.Hashids;

public class IdGenerator {
  private static final String availChars = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ.-_!$";
  private final AtomicLong counter = new AtomicLong(0);
  private final Hashids hashids = new Hashids("dvr4.", 4, availChars);
  private String prefix;

  public IdGenerator() {
    this.prefix = "";
  }
  public IdGenerator(String prefix) {
    this.prefix = prefix;
  }

  private static String smallestNonExistingPrefix(Stream<String> existingIds) {
    final char preferredPrefixChar = '.';
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

  public String getPrefix() {
    return prefix;
  }

  public IdGenerator setPrefix(Stream<String> existingIds) {
    this.prefix = smallestNonExistingPrefix(existingIds);
    return this;
  }

  public String next(){
    return prefix + hashids.encode(counter.incrementAndGet());
  }
}
