package org.col.dao;

import com.google.common.collect.Lists;
import org.apache.ibatis.session.SqlSession;
import org.col.api.*;
import org.col.db.NotFoundException;
import org.col.db.mapper.NameMapper;
import org.col.db.mapper.ReferenceMapper;
import org.col.db.mapper.VerbatimRecordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

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
		NameMapper mapper = session.getMapper(NameMapper.class);
		int total = mapper.count(datasetKey);
		List<Name> result = mapper.list(datasetKey, page);
		return new PagingResultSet<>(page, total, result);
	}

	public Name get(int key) {
		NameMapper mapper = session.getMapper(NameMapper.class);
		return mapper.get(key);
	}

	public Name get(int datasetKey, String id) {
		return get(lookup(datasetKey, id));
	}

	public void create(Name name) {
		NameMapper mapper = session.getMapper(NameMapper.class);
		mapper.create(name);
	}

	/**
	 * Lists all homotypic basionymGroup based on the same basionym
	 */
	public List<Name> basionymGroup(int key) {
		NameMapper mapper = session.getMapper(NameMapper.class);
		return mapper.basionymGroup(key);
	}

	/**
	 * Lists all homotypic basionymGroup based on the same basionym
	 */
	public List<Name> basionymGroup(int datasetKey, String id) {
		return basionymGroup(lookup(datasetKey, id));
	}

	/**
	 * Adds a new synonym link for an existing taxon and synonym name. This link is
	 * used for both a hetero- or homotypic synonym.
	 *
	 * @param taxonKey
	 *          the key of the accepted Taxon
	 * @param synonymNameKey
	 *          the key of the synonym Name
	 */
	public void addSynonym(int datasetKey, int taxonKey, int synonymNameKey) {
		NameMapper mapper = session.getMapper(NameMapper.class);
		mapper.addSynonym(datasetKey, taxonKey, synonymNameKey);
	}

	/**
	 * Assemble a synonymy object from the list of synonymy names for a given
	 * accepted taxon.
	 */
	public Synonymy getSynonymy(int taxonKey) {
		NameMapper mapper = session.getMapper(NameMapper.class);
		Synonymy syn = new Synonymy();
		int lastBasKey = -1;
		List<Name> homotypics = null;
		for (Name n : mapper.synonyms(taxonKey)) {
			int basKey = n.getBasionymKey() == null ? n.getKey() : n.getBasionymKey();
			if (lastBasKey == -1 || basKey != lastBasKey) {
				lastBasKey = basKey;
				// new homotypic group
				if (homotypics != null) {
					syn.addHomotypicGroup(homotypics);
				}
				homotypics = Lists.newArrayList();
			}
			homotypics.add(n);
		}
		if (homotypics != null) {
			syn.addHomotypicGroup(homotypics);
		}
		return syn;
	}

	public PagingResultSet<Name> search(NameSearch query, Page page) {
		if (query.getQ() != null) {
			query.setQ(query.getQ() + ":*");
		}
		NameMapper mapper = session.getMapper(NameMapper.class);
		int total = mapper.countSearchResults(query);
		List<Name> result = mapper.search(query, page);
		return new PagingResultSet<>(page, total, result);
	}

	public PagedReference getPublishedIn(int datasetKey, int nameKey) {
		ReferenceMapper mapper = session.getMapper(ReferenceMapper.class);
		return mapper.getPublishedIn(datasetKey, nameKey);
	}

	public VerbatimRecord getVerbatim(int datasetKey, String id) {
		VerbatimRecordMapper mapper = session.getMapper(VerbatimRecordMapper.class);
		return mapper.getByName(datasetKey, id);
	}

	private int lookup(int datasetKey, String id) throws NotFoundException {
		NameMapper mapper = session.getMapper(NameMapper.class);
		Integer key = mapper.lookupKey(datasetKey, id);
		if (key == null) {
			throw new NotFoundException(Name.class, datasetKey, id);
		}
		return key;
	}

}
