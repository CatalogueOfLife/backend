package org.col.dao;

import java.util.List;

import org.apache.ibatis.session.SqlSession;
import org.col.api.Page;
import org.col.api.PagingResultSet;
import org.col.api.Reference;
import org.col.db.mapper.ReferenceMapper;

public class ReferenceDao {

	private final SqlSession session;

	public ReferenceDao(SqlSession sqlSession) {
		this.session = sqlSession;
	}

	public int count(int datasetKey) {
		ReferenceMapper mapper = session.getMapper(ReferenceMapper.class);
		return mapper.count(datasetKey);
	}

	public PagingResultSet<Reference> list(int datasetKey, Page page) {
		ReferenceMapper mapper = session.getMapper(ReferenceMapper.class);
		int total = mapper.count(datasetKey);
		List<Reference> result = mapper.list(datasetKey, page);
		return new PagingResultSet<>(page, total, result);
	}

	public Reference getByKey(int key) {
		ReferenceMapper mapper = session.getMapper(ReferenceMapper.class);
		return mapper.getByKey(key);
	}

	public Reference get(int datasetKey, String id) {
		ReferenceMapper mapper = session.getMapper(ReferenceMapper.class);
		return mapper.get(datasetKey, id);
	}

	public void create(Reference ref) {
		ReferenceMapper mapper = session.getMapper(ReferenceMapper.class);
		mapper.create(ref);
	}

}
