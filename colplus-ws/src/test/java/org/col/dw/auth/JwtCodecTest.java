package org.col.dw.auth;

import java.util.HashSet;
import java.util.Set;

import org.col.api.TestEntityGenerator;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class JwtCodecTest {
  
  @Test
  public void generate() throws Exception {
    JwtCodec jwt = new JwtCodec("vchjbds65–HJ#2esy2434d");
    Set<String> codes = new HashSet<>();
    for (int x = 0; x<100; x++) {
      String code = jwt.generate(TestEntityGenerator.USER_EDITOR);
      System.out.println(code);
      codes.add(code);
    }
    assertEquals(100, codes.size());
  }
}