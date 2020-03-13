package life.catalogue.common.collection;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import static org.junit.Assert.*;

public class DefaultMapTest {
  
  @Test
  public void testCounter(){
    DefaultMap<Integer, AtomicInteger> cnt = DefaultMap.createCounter();
    cnt.get(10).incrementAndGet();
    cnt.get(10).incrementAndGet();
    cnt.get(100).incrementAndGet();
    cnt.get(-1).incrementAndGet();
    
    assertEquals(2, cnt.get(10).get());
    assertEquals(1, cnt.get(100).get());
    assertEquals(1, cnt.get(-1).get());
    assertEquals(0, cnt.get(-21).get());
    assertEquals(0, cnt.get(0).get());
    assertEquals(0, cnt.get(334567).get());
  }
  
}