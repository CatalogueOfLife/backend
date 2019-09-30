package org.col.common.io;

import java.io.*;
import java.nio.charset.StandardCharsets;

import com.google.common.io.Files;

public class Utf8IOUtils {

  public static BufferedWriter writerFromFile(File f) throws IOException {
    Files.createParentDirs(f);
    return writerFromStream(new FileOutputStream(f));
  }
  
  public static BufferedWriter writerFromStream(OutputStream stream) {
    return new BufferedWriter(new OutputStreamWriter(stream, StandardCharsets.UTF_8));
  }
  
  public static BufferedReader readerFromFile(File f) throws IOException {
    return readerFromStream(new FileInputStream(f));
  }
  
  public static BufferedReader readerFromStream(InputStream stream) {
    return new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
  }
}
