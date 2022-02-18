package life.catalogue.common.collection;

import org.junit.Test;

import static org.junit.Assert.*;

public class Int2IntBiMapTest {

  @Test
  public void size() {
    Int2IntBiMap m = new Int2IntBiMap();
    assertEquals(0, m.size());
    m.put(1,100);
    assertEquals(1, m.size());
    m.put(1,2);
    assertEquals(1, m.size());
    m.put(2,100);
    m.put(3,101);
    assertEquals(3, m.size());
  }

  @Test
  public void isEmpty() {
    Int2IntBiMap m = new Int2IntBiMap();
    assertTrue(m.isEmpty());
    m.put(1,2);
    assertFalse(m.isEmpty());
  }

  @Test
  public void clear() {
    Int2IntBiMap m = new Int2IntBiMap();
    assertTrue(m.isEmpty());
    m.put(1,100);
    m.put(1,2);
    m.put(2,100);
    m.put(3,101);
    assertFalse(m.isEmpty());
    m.clear();
    assertTrue(m.isEmpty());
  }

  @Test
  public void containsKey() {
    Int2IntBiMap m = new Int2IntBiMap();
    assertFalse(m.containsKey(2));
    m.put(1,2);
    assertFalse(m.containsKey(2));
    assertTrue(m.containsKey(1));
    m.put(2,1);
    assertTrue(m.containsKey(2));
    assertTrue(m.containsKey(1));
  }

  @Test
  public void containsValue() {
    Int2IntBiMap m = new Int2IntBiMap();
    assertFalse(m.containsValue(2));
    m.put(1,2);
    assertFalse(m.containsValue(1));
    assertTrue(m.containsValue(2));
    m.put(2,1);
    assertTrue(m.containsValue(2));
    assertTrue(m.containsValue(1));
  }

  @Test
  public void getValue() {
    Int2IntBiMap m = new Int2IntBiMap();
    m.put(1,2);
    assertEquals(2, m.getValue(1));
    m.put(2,1);
    assertEquals(2, m.getValue(1));
    assertEquals(1, m.getValue(2));
  }

  @Test
  public void getKey() {
    Int2IntBiMap m = new Int2IntBiMap();
    m.put(1,2);
    assertEquals(1, m.getKey(2));
    m.put(2,1);
    assertEquals(2, m.getKey(1));
    assertEquals(1, m.getKey(2));
  }

}