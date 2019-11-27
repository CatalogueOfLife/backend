package life.catalogue.dao;

import java.io.File;
import java.util.List;
import java.util.function.BiFunction;
import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.ResultPage;
import life.catalogue.api.search.DatasetSearchRequest;
import life.catalogue.common.io.DownloadUtil;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.img.ImageService;
import life.catalogue.img.LogoUpdateJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatasetDao extends EntityDao<Integer, Dataset, DatasetMapper> {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(DatasetDao.class);
  
  private final DownloadUtil downloader;
  private final ImageService imgService;
  private final BiFunction<Integer, String, File> scratchFileFunc;
  
  public DatasetDao(SqlSessionFactory factory,
                    DownloadUtil downloader,
                    ImageService imgService,
                    BiFunction<Integer, String, File> scratchFileFunc) {
    super(false, factory, DatasetMapper.class);
    this.downloader = downloader;
    this.imgService = imgService;
    this.scratchFileFunc = scratchFileFunc;
  }
  
  public ResultPage<Dataset> list(Page page) {
    return super.list(DatasetMapper.class, page);
  }
  
  public ResultPage<Dataset> search(@Nullable DatasetSearchRequest nullableRequest, @Nullable Page page) {
    page = page == null ? new Page() : page;
    final DatasetSearchRequest req = nullableRequest == null || nullableRequest.isEmpty() ? new DatasetSearchRequest() : nullableRequest;
    if (req.getSortBy() == null) {
      if (!StringUtils.isBlank(req.getQ())) {
        req.setSortBy(DatasetSearchRequest.SortBy.RELEVANCE);
      } else {
        req.setSortBy(DatasetSearchRequest.SortBy.KEY);
      }
    } else if (req.getSortBy() == DatasetSearchRequest.SortBy.RELEVANCE && StringUtils.isBlank(req.getQ())) {
      req.setQ(null);
      req.setSortBy(DatasetSearchRequest.SortBy.KEY);
    }
    
    try (SqlSession session = factory.openSession()){
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      List<Dataset> result = dm.search(req, page);
      return new ResultPage<>(page, result, () -> dm.count(req));
    }
  }
  
  @Override
  protected void createAfter(Dataset obj, int user, DatasetMapper mapper, SqlSession session) {
    pullLogo(obj);
  }
  
  @Override
  protected void updateAfter(Dataset obj, Dataset old, int user, DatasetMapper mapper, SqlSession session) {
    pullLogo(obj);
  }
  
  private void pullLogo(Dataset d) {
    LogoUpdateJob.updateDatasetAsync(d, factory, downloader, scratchFileFunc, imgService);
  }
  
}
