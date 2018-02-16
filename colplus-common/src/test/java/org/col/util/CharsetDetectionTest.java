package org.col.util;

import com.google.common.base.Charsets;
import org.junit.Test;

import java.nio.charset.Charset;

import static org.junit.Assert.assertEquals;

/**
 *
 */
public class CharsetDetectionTest {

  @Test
  public void detectEncoding() throws Exception {
    assertEquals(Charsets.UTF_16BE, CharsetDetection.detectEncoding(getClass().getResource("/UTF16BE.txt").openStream()));
    assertEquals(Charsets.ISO_8859_1, CharsetDetection.detectEncoding(getClass().getResource("/latin1.txt").openStream()));
    assertEquals(Charset.forName("windows-1252"), CharsetDetection.detectEncoding(getClass().getResource("/windows.txt").openStream()));
  }

}