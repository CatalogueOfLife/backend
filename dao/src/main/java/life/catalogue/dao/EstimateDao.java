package life.catalogue.dao;

import life.catalogue.api.model.Page;
import life.catalogue.api.model.ResultPage;
import life.catalogue.api.model.SpeciesEstimate;
import life.catalogue.api.search.EstimateSearchRequest;
import life.catalogue.db.mapper.EstimateMapper;

import java.util.List;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EstimateDao extends DatasetEntityDao<Integer, SpeciesEstimate, EstimateMapper> {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(EstimateDao.class);
  

  public EstimateDao(SqlSessionFactory factory) {
    super(true, factory, EstimateMapper.class);
  }
  
  public ResultPage<SpeciesEstimate> search(EstimateSearchRequest request, Page page) {
    Page p = page == null ? new Page() : page;
    try (SqlSession session = factory.openSession()) {
      EstimateMapper mapper = session.getMapper(EstimateMapper.class);
      List<SpeciesEstimate> result = mapper.search(request, p);
      return new ResultPage<>(p, result, () -> mapper.countSearch(request));
    }
  }
  
}
