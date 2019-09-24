package org.col.common.text;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.*;

public class IdConverterTest {
  
  @Test
  public void encode() {
    int[] ids = new int[]{18, 1089, 1781089, 4781089, 12781089, Integer.MAX_VALUE};
    List<IdConverter> hashids = new ArrayList<>();
    hashids.add(IdConverter.HEX);
    hashids.add(IdConverter.LATIN36);
    hashids.add(IdConverter.BASE64);
  
    for (int id : ids) {
      System.out.println("\n" + id);
      for (IdConverter hid : hashids) {
        System.out.println(hid.encode(id));
      }
    }
  }
}