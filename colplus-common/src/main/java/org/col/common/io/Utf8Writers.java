package org.col.common.io;

import java.io.*;

import com.google.common.io.Files;

public class Utf8Writers {

  public static Writer fromFile(File f) throws IOException {
    Files.createParentDirs(f);
    return fromStream(new FileOutputStream(f));
  }
  
  public static Writer fromStream(OutputStream stream) throws IOException {
    try {
      return new BufferedWriter(new OutputStreamWriter(stream, "UTF8"));
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
  }
}
