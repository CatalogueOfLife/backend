package org.col.importer;

import java.net.URI;

import org.col.api.model.Media;
import org.col.api.vocab.MediaType;
import org.junit.Test;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertNull;

public class MediaInterpreterTest {
  
  @Test
  public void detectType() {
    Media m = new Media();
    MediaInterpreter.detectType(m);
    assertNull(m.getType());
    
    m.setFormat("image/png");
    MediaInterpreter.detectType(m);
    assertEquals(MediaType.IMAGE, m.getType());
  
    m.setFormat("image/png");
    URI uri = URI.create("http://img.gro.org/234567zu");
    m.setUrl(uri);
    MediaInterpreter.detectType(m);
    assertEquals(MediaType.IMAGE, m.getType());
    assertEquals(uri, m.getUrl());
    assertNull(m.getLink());
  
    m = new Media();
    m.setFormat("text/html");
    m.setUrl(uri);
    MediaInterpreter.detectType(m);
    assertNull(m.getType());
    assertEquals(uri, m.getLink());
    assertNull(m.getUrl());
    assertNull(m.getType());
  
    m = new Media();
    uri = URI.create("http://img.gro.org/234567.JPEG");
    m.setUrl(uri);
    MediaInterpreter.detectType(m);
    assertEquals(MediaType.IMAGE, m.getType());
    assertEquals(uri, m.getUrl());
    assertNull(m.getLink());
  }

}