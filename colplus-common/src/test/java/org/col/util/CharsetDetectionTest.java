package org.col.util;

import com.google.common.base.Charsets;
import org.col.util.io.CharsetDetection;
import org.junit.Test;

import java.nio.charset.Charset;

import static org.junit.Assert.assertEquals;

/**
 *
 */
public class CharsetDetectionTest {

  @Test
  public void detectEncoding() throws Exception {
    assertEquals(Charsets.UTF_8, CharsetDetection.detectEncoding(getClass().getResource("/utf8.txt").openStream()));
    assertEquals(Charsets.UTF_16BE, CharsetDetection.detectEncoding(getClass().getResource("/UTF16BE.txt").openStream()));
    assertEquals(Charsets.ISO_8859_1, CharsetDetection.detectEncoding(getClass().getResource("/latin1.txt").openStream()));
    assertEquals(Charset.forName("windows-1252"), CharsetDetection.detectEncoding(getClass().getResource("/windows.txt").openStream()));
  }

  private static void showBits(byte param) {
    int mask = 1 << 8;

    for (int i = 1; i <= 8; i++,
        param <<= 1) {
      System.out.print((param & mask) ==
          0 ? "0" : "1");
      if (i % 8 == 0)
        System.out.print(" ");
    }
    System.out.println();
  }
}