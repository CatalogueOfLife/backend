package org.col.common.io;

import java.io.*;
import java.nio.charset.StandardCharsets;

import com.google.common.io.Files;

public class Utf8Writers {

  public static Writer fromFile(File f) throws IOException {
    Files.createParentDirs(f);
    return fromStream(new FileOutputStream(f));
  }
  
  public static Writer fromStream(OutputStream stream) {
    return new BufferedWriter(new OutputStreamWriter(stream, StandardCharsets.UTF_8));
  }
}
