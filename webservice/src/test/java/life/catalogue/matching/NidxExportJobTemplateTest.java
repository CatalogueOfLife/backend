package life.catalogue.matching;

import java.util.List;

import life.catalogue.api.model.Dataset;
import life.catalogue.dao.DatasetImportDao;

import life.catalogue.db.mapper.DatasetMapper;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class NidxExportJobTemplateTest {

  @Mock
  SqlSessionFactory factory;
  @Mock
  DatasetMapper mapper;
  @Mock
  SqlSession session;

  @Before
  public void init() {
    when(factory.openSession()).thenReturn(session);
    when(session.getMapper(any())).thenReturn(mapper);
    when(mapper.get(any())).thenReturn(new Dataset());
  }

  @Test
  public void dupe() {
    var job1 = new NidxExportJob(List.of(1,2,3,4), 1, 100, factory, null);
    var job2 = new NidxExportJob(List.of(2,3,4,1), 1, 101, factory, null);
    var job3 = new NidxExportJob(List.of(1,2,3,4), 2, 101, factory, null);
    var job4 = new NidxExportJob(List.of(1,2,3,4,5), 1, 100, factory, null);
    assertNotEquals(job1, job2);
    assertNotEquals(job1, job3);
    assertNotEquals(job1, job4);

    assertTrue(job1.isDuplicate(job2));
    assertFalse(job1.isDuplicate(job3));
    assertFalse(job1.isDuplicate(job4));
  }
}