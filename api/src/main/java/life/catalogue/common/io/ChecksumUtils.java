package life.catalogue.common.io;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class ChecksumUtils {

  private static final char[] hexCode = "0123456789ABCDEF".toCharArray();

  public static String getMD5Checksum(File file) throws IOException {
    return getFileChecksum("MD5", file);
  }
  
  public static String getSHAChecksum(File file) throws IOException {
    return getFileChecksum("SHA-1", file);
  }
  
  private static String getFileChecksum(String algorithmName, File file) throws IOException {
    MessageDigest digest;
    try {
      digest = MessageDigest.getInstance(algorithmName);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
    
    //Get file input stream for reading the file content
    try (FileInputStream fis = new FileInputStream(file)) {
      //Create byte array to read data in chunks
      byte[] byteArray = new byte[1024];
      int bytesCount = 0;

      //Read file data and update in message digest
      while ((bytesCount = fis.read(byteArray)) != -1) {
        digest.update(byteArray, 0, bytesCount);
      }
    }

    //Get the hash's bytes
    byte[] bytes = digest.digest();

    return toHexBinary(bytes);
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
