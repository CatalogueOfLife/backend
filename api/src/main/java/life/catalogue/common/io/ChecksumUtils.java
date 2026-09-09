package life.catalogue.common.io;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ChecksumUtils {

  private static final char[] hexCode = "0123456789ABCDEF".toCharArray();
  private static final int BUFFER_SIZE = 64 * 1024;

  public static String getMD5Checksum(File file) throws IOException {
    return getFileChecksum("MD5", file);
  }

  /**
   * Aggregate checksum over a set of files, each keyed by its path relative to the given folder.
   * Independent of the order the files are given in, but sensitive to those relative paths,
   * so renaming or moving a file within the folder changes the checksum.
   *
   * @param folder the folder the files are addressed relative to
   * @param files the files to include, in any order
   */
  public static String getMD5Checksum(Path folder, Collection<Path> files) throws IOException {
    final Path base = folder.toAbsolutePath().normalize();
    List<String> entries = new ArrayList<>(files.size());
    for (Path f : files) {
      entries.add(base.relativize(f.toAbsolutePath().normalize()) + "\t" + getMD5Checksum(f.toFile()));
    }
    // sorted, so the order the files were discovered in does not matter
    Collections.sort(entries);

    MessageDigest digest = digest("MD5");
    for (String entry : entries) {
      digest.update(entry.getBytes(StandardCharsets.UTF_8));
      digest.update((byte) '\n');
    }
    return toHexBinary(digest.digest());
  }

  public static String getSHAChecksum(File file) throws IOException {
    return getFileChecksum("SHA-1", file);
  }

  private static MessageDigest digest(String algorithmName) {
    try {
      return MessageDigest.getInstance(algorithmName);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  private static String getFileChecksum(String algorithmName, File file) throws IOException {
    MessageDigest digest = digest(algorithmName);

    try (FileInputStream fis = new FileInputStream(file)) {
      byte[] byteArray = new byte[BUFFER_SIZE];
      int bytesCount;

      while ((bytesCount = fis.read(byteArray)) != -1) {
        digest.update(byteArray, 0, bytesCount);
      }
    }

    return toHexBinary(digest.digest());
  }

  public static String toHexBinary(byte[] data) {
    StringBuilder r = new StringBuilder(data.length * 2);
    for (byte b : data) {
      r.append(hexCode[(b >> 4) & 0xF]);
      r.append(hexCode[(b & 0xF)]);
    }
    return r.toString();
  }
}
