package org.col.dao;

import org.junit.Test;

import static org.junit.Assert.*;

public class CatCopyTest {
  
  @Test
  public void latinName() throws Exception {
    assertEquals("Abies", CatCopy.latinName("Abies"));
    assertEquals("Döring", CatCopy.latinName("Döring"));
    assertEquals("Bào wén dōng fāng tún", CatCopy.latinName("Bào wén dōng fāng tún"));
    assertEquals("bào wén duō jì tún", CatCopy.latinName("豹紋多紀魨"));
  }
  
  @Test
  public void asciiName() throws Exception {
    assertEquals("Abies", CatCopy.asciiName("Abiés"));
    assertEquals("Döring", CatCopy.latinName("Döring"));
    assertEquals("Bao wen dong fang tun", CatCopy.asciiName("Bào wén dōng fāng tún"));
    assertEquals("bao wen duo ji tun", CatCopy.asciiName("豹紋多紀魨"));
  }
}