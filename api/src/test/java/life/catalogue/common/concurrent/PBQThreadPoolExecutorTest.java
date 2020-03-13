package life.catalogue.common.concurrent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PBQThreadPoolExecutorTest {
  
  static class Runme implements Runnable {
    final int num;
  
    Runme(int num) {
      this.num = num;
    }
  
    @Override
    public void run() {
      System.out.println(num);
    }
  }
  
  @Test
  public void ordering() {
    PBQThreadPoolExecutor<Runme> exec = new PBQThreadPoolExecutor<>(1, 10);
    List<PBQThreadPoolExecutor.ComparableFutureTask> tasks = new ArrayList<>();
    tasks.add(exec.new ComparableFutureTask(new Runme(1), true));
    tasks.add(exec.new ComparableFutureTask(new Runme(2), false));
    tasks.add(exec.new ComparableFutureTask(new Runme(3), true));
    tasks.add(exec.new ComparableFutureTask(new Runme(4), false));
    tasks.add(exec.new ComparableFutureTask(new Runme(5), true));
  
    int h1 = tasks.get(0).hashCode();
    int h2 = tasks.get(1).hashCode();
    int h3 = tasks.get(2).hashCode();
    int h4 = tasks.get(3).hashCode();
    int h5 = tasks.get(4).hashCode();
    
    Collections.sort(tasks);
    
    assertEquals(h1, tasks.get(0).hashCode());
    assertEquals(h3, tasks.get(1).hashCode());
    assertEquals(h5, tasks.get(2).hashCode());
    assertEquals(h2, tasks.get(3).hashCode());
    assertEquals(h4, tasks.get(4).hashCode());
    
  }
}