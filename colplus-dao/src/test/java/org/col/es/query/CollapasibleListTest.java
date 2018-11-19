package org.col.es.query;

import com.fasterxml.jackson.core.JsonProcessingException;

import org.col.es.EsModule;
import org.col.es.util.CollapsibleList;
import org.junit.Test;

public class CollapasibleListTest {
  
  
  @Test
  public void test1() throws JsonProcessingException {
    CollapsibleList<String> list = new CollapsibleList<>();
    System.out.println(EsModule.MAPPER.writeValueAsString(list));
  }
 
  
  @Test
  public void test2() throws JsonProcessingException {
    CollapsibleList<String> list = new CollapsibleList<>();
    list.add("one");
    System.out.println(EsModule.MAPPER.writeValueAsString(list));
  }
 
  
  @Test
  public void test3() throws JsonProcessingException {
    CollapsibleList<String> list = new CollapsibleList<>();
    list.add("one");
    list.add("two");
    list.add("three");
    System.out.println(EsModule.MAPPER.writeValueAsString(list));
  }

}
