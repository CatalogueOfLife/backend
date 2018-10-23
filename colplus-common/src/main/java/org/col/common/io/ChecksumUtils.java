package org.col.common.io;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.xml.bind.DatatypeConverter;

public class ChecksumUtils {
  
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
    FileInputStream fis = new FileInputStream(file);
    
    //Create byte array to read data in chunks
    byte[] byteArray = new byte[1024];
    int bytesCount = 0;
    
    //Read file data and update in message digest
    while ((bytesCount = fis.read(byteArray)) != -1) {
      digest.update(byteArray, 0, bytesCount);
    }
    
    //close the stream; We don't need it now.
    fis.close();
    
    //Get the hash's bytes
    byte[] bytes = digest.digest();
  
    return DatatypeConverter.printHexBinary(bytes).toUpperCase();
  }
}
