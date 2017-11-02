package org.col.dao;

import java.util.List;

import org.apache.ibatis.session.SqlSession;
import org.col.api.PagedReference;
import org.col.api.Name;
import org.col.api.Page;
import org.col.api.PagingResultSet;
import org.col.api.VerbatimRecord;
import org.col.db.mapper.NameMapper;
import org.col.db.mapper.ReferenceMapper;
import org.col.db.mapper.VerbatimRecordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NameDao {

	@SuppressWarnings("unused")
	private static final Logger LOG = LoggerFactory.getLogger(NameDao.class);

	private final SqlSession session;

	public NameDao(SqlSession sqlSession) {
		this.session = sqlSession;
	}

	public int count(int datasetKey) {
		NameMapper mapper = session.getMapper(NameMapper.class);
		return mapper.count(datasetKey);
	}

	public PagingResultSet<Name> list(int datasetKey, Page page) {
		Page p = page == null ? new Page() : page;
		NameMapper mapper = session.getMapper(NameMapper.class);
		int total = mapper.count(datasetKey);
		List<Name> result = mapper.list(datasetKey, p);
		return new PagingResultSet<>(page, total, result);
	}

	public Name getByKey(int key) {
		NameMapper mapper = session.getMapper(NameMapper.class);
		return mapper.getByKey(key);
	}

	public Name get(int datasetKey, String id) {
		NameMapper mapper = session.getMapper(NameMapper.class);
		return mapper.get(datasetKey, id);
	}

	public void create(Name name) {
		NameMapper mapper = session.getMapper(NameMapper.class);
		mapper.create(name);
	}

	public List<Name> synonyms(int datasetKey, String id) {
		NameMapper mapper = session.getMapper(NameMapper.class);
		return mapper.synonyms(datasetKey, id);
	}

	public List<Name> synonymsByKey(int key) {
		NameMapper mapper = session.getMapper(NameMapper.class);
		return mapper.synonymsByKey(key);
	}

	public PagingResultSet<Name> search(int datasetKey, String q) {
		NameMapper mapper = session.getMapper(NameMapper.class);
		return mapper.search(datasetKey, q);
	}

	public PagedReference getPublishedIn(int datasetKey, String id) {
		ReferenceMapper mapper = session.getMapper(ReferenceMapper.class);
		return mapper.getPublishedIn(datasetKey, id);
	}

	public VerbatimRecord getVerbatim(int datasetKey, String id) {
		VerbatimRecordMapper mapper = session.getMapper(VerbatimRecordMapper.class);
		return mapper.getByName(datasetKey, id);
	}

}
