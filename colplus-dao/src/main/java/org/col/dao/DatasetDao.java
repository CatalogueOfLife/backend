package org.col.dao;

import java.io.File;
import java.util.List;
import javax.annotation.Nullable;

import com.google.common.base.Function;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.Dataset;
import org.col.api.model.Page;
import org.col.api.model.ResultPage;
import org.col.api.search.DatasetSearchRequest;
import org.col.common.io.DownloadUtil;
import org.col.db.mapper.DatasetMapper;
import org.col.img.ImageService;
import org.col.img.LogoUpdateJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatasetDao extends CrudIntDao<Dataset> {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(DatasetDao.class);
  
  private final DownloadUtil downloader;
  private final ImageService imgService;
  private final Function<Integer, File> scratchDirFunc;
  
  public DatasetDao(SqlSessionFactory factory,
                    DownloadUtil downloader,
                    ImageService imgService,
                    Function<Integer, File> scratchDirFunc) {
    super(factory, DatasetMapper.class);
    this.downloader = downloader;
    this.imgService = imgService;
    this.scratchDirFunc = scratchDirFunc;
  }
  
  public ResultPage<Dataset> search(@Nullable DatasetSearchRequest req, @Nullable Page page) {
    page = page == null ? new Page() : page;
    req = req == null || req.isEmpty() ? new DatasetSearchRequest() : req;
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
      int total = dm.count(req);
      List<Dataset> result = dm.search(req, page);
      return new ResultPage<>(page, total, result);
    }
  }
  
  @Override
  public void create(Dataset obj) {
    super.create(obj);
    pullLogo(obj);
  }
  
  @Override
  public int update(Dataset obj) {
    int upd = super.update(obj);
    pullLogo(obj);
    return upd;
  }
  
  private void pullLogo(Dataset d) {
    LogoUpdateJob.updateDatasetAsync(d, factory, downloader, scratchDirFunc, imgService);
  }
  
}
