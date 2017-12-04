package org.col.dao;

import java.util.List;

import javax.annotation.Nullable;

import org.apache.ibatis.session.SqlSession;
import org.col.api.Dataset;
import org.col.api.Page;
import org.col.api.PagingResultSet;
import org.col.db.mapper.DatasetMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatasetDao {

	@SuppressWarnings("unused")
	private static final Logger LOG = LoggerFactory.getLogger(DatasetDao.class);

	private final SqlSession session;

	public DatasetDao(SqlSession sqlSession) {
		this.session = sqlSession;
	}

	public PagingResultSet<Dataset> search(String q, @Nullable Page page) {
		page = page == null ? new Page() : page;
		String query = q + ":*"; // Enable "starts_with" term matching
		DatasetMapper mapper = session.getMapper(DatasetMapper.class);
		int total = mapper.countSearchResults(query);
		List<Dataset> result = mapper.search(query, page);
		return new PagingResultSet<>(page, total, result);
	}

}
