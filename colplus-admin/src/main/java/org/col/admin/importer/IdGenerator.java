package org.col.admin.importer;

import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import org.hashids.Hashids;

public class IdGenerator {
  private final AtomicLong counter = new AtomicLong(0);
  private final String prefix;
  private final Hashids hashids = new Hashids("dvr4.", 4,
      "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ.-_$!");

  private IdGenerator(String prefix) {
    this.prefix = prefix;
  }

  public static IdGenerator prefixed(String prefix) {
    return new IdGenerator(prefix);
  }

  public static IdGenerator prefixed(Stream<String> existingIds) {
    return new IdGenerator(smallestNonExistingPrefix(existingIds));
  }

  private static String smallestNonExistingPrefix(Stream<String> existingIds) {
    return ".norm.";
  }

  public String getPrefix() {
    return prefix;
  }

  public String next(){
    return prefix + hashids.encode(counter.incrementAndGet());
  }
}
