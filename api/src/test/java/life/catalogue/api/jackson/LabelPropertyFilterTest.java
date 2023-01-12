package life.catalogue.api.jackson;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LabelPropertyFilterTest {

  @Test
  public void label() throws Exception {

    ObjectMapper mapper = new ObjectMapper();
    FilterProvider filters = new SimpleFilterProvider().addFilter(LabelPropertyFilter.NAME, new LabelPropertyFilter());
    mapper.setFilterProvider(filters);

    Person p = new Person("Carla", 12);
    var regular = mapper.writeValueAsString(p);
    System.out.println(regular);
    assertTrue(regular.contains("\"label\""));
    assertEquals(1, StringUtils.countMatches(regular, "\"label\""));

    var o = new Org("GBIF", p);
    var org = mapper.writeValueAsString(o);
    System.out.println(org);
    assertTrue(org.contains("\"label\""));
    assertEquals(1, StringUtils.countMatches(org, "\"label\""));
  }

  public static class Person {
    public final String name;
    public final int age;

    public Person(String name, int age) {
      this.name = name;
      this.age = age;
    }

    public String getLabel() {
      return name + " (" + age+")";
    }
  }

  public static class Org {
    public final String name;
    @JsonFilter(LabelPropertyFilter.NAME)
    public final List<Person> people;

    public Org(String name, Person... people) {
      this.name = name;
      this.people = List.of(people);
    }

    public String getLabel() {
      return name + " [" + people.size()+"]";
    }
  }

}