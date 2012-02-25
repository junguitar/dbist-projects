/**
 * Copyright 2011-2012 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.dbist.dml;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import net.sf.common.util.ReflectionUtils;
import net.sf.common.util.ValueUtils;

import org.dbist.exception.DataNotFoundException;
import org.dbist.exception.DbistRuntimeException;
import org.dbist.metadata.Table;
import org.dbist.processor.Preprocessor;
import org.springframework.beans.factory.InitializingBean;

/**
 * @author Steve M. Jung
 * @since 2012. 1. 5. (version 0.0.1)
 */
public abstract class AbstractDml implements Dml, InitializingBean {
	private Preprocessor preprocessor;

	@Override
	public Table getTable(String name) {
		Class<?> clazz = getClass(name);
		return clazz == null ? null : getTable(clazz);
	}

	public Preprocessor getPreprocessor() {
		return preprocessor;
	}
	@Override
	public void setPreprocessor(Preprocessor preprocessor) {
		this.preprocessor = preprocessor;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
	}

	protected <T> T select(List<T> list) {
		if (ValueUtils.isEmpty(list))
			return null;
		else if (list.size() > 1)
			throw new DbistRuntimeException("Selected data size is not 1: " + list.size());
		return list.get(0);
	}

	@SuppressWarnings("unchecked")
	protected Query toQuery(Table table, Object condition, String... fieldNames) throws Exception {
		if (condition instanceof Query)
			return (Query) condition;
		boolean byFieldName = fieldNames != null && fieldNames.length != 0;
		Set<String> fieldNameSet = byFieldName ? ValueUtils.toSet(fieldNames) : null;
		Query query = new Query();
		if (condition instanceof HttpServletRequest) {
			HttpServletRequest request = (HttpServletRequest) condition;
			Map<String, String[]> paramMap = request.getParameterMap();
			for (String key : paramMap.keySet()) {
				if (byFieldName) {
					if (!fieldNameSet.contains(key))
						continue;
					fieldNameSet.remove(key);
				}
				Field field = table.getField(key);
				if (field == null)
					continue;
				String[] values = paramMap.get(key);
				if (ValueUtils.isEmpty(values))
					continue;
				for (String value : values)
					query.addFilter(key, value);
			}
			if (paramMap.containsKey("pageIndex"))
				query.setPageIndex(ValueUtils.toInteger(request.getParameter("pageIndex"), 0));
			if (paramMap.containsKey("pageSize"))
				query.setPageSize(ValueUtils.toInteger(request.getParameter("pageSize"), 0));
			if (paramMap.containsKey("operator") && table.getField("operator") == null)
				query.setOperator(request.getParameter("operator"));
		} else if (condition instanceof Map) {
			Map<String, Object> map = (Map<String, Object>) condition;
			for (String key : map.keySet()) {
				if (byFieldName) {
					if (!fieldNameSet.contains(key))
						continue;
					fieldNameSet.remove(key);
				}
				query.addFilter(key, map.get(key));
			}
		} else if (condition instanceof Filters) {
			ValueUtils.populate(condition, query);
		} else if (condition instanceof List) {
			query.setFilter((List<Filter>) condition);
		} else if (condition.getClass().isArray()) {
			query.setFilter(ValueUtils.toList((Filter[]) condition));
		} else if (condition instanceof Filter) {
			query.addFilter((Filter) condition);
		} else {
			for (Field field : ReflectionUtils.getFieldList(condition, true)) {
				String key = field.getName();
				if (byFieldName) {
					if (!fieldNameSet.contains(key))
						continue;
					fieldNameSet.remove(key);
				}
				Object value = field.get(condition);
				if (value == null)
					continue;
				query.addFilter(key, value);
			}
		}
		if (byFieldName && fieldNameSet.size() != 0)
			throw new IllegalArgumentException("Some of fieldName condition was not in "
					+ (String[]) fieldNameSet.toArray(new String[fieldNameSet.size()]));
		return query;
	}
	protected Query toPkQuery(Object obj, Object condition) throws Exception {
		Class<?> clazz;
		if (obj instanceof Class)
			clazz = (Class<?>) obj;
		else if (obj instanceof String)
			clazz = getClass((String) obj);
		else
			clazz = obj.getClass();
		Query query = new Query();
		try {
			if (condition == null || condition instanceof Query)
				return (Query) condition;
			Table table = getTable(clazz);
			if (condition instanceof List) {
				if (ValueUtils.isEmpty(condition))
					return query;
				@SuppressWarnings("unchecked")
				List<?> list = (List<Object>) condition;
				if (ValueUtils.isPrimitive(list.get(0))) {
					int i = 0;
					int size = list.size();
					for (String pkFieldName : table.getPkFieldNames()) {
						query.addFilter(pkFieldName, list.get(i++));
						if (i == size)
							break;
					}
					return query;
				}
			} else if (condition.getClass().isArray()) {
				if (ValueUtils.isEmpty(condition))
					return query;
				Object[] array = (Object[]) condition;
				int i = 0;
				int size = array.length;
				for (String pkFieldName : table.getPkFieldNames()) {
					query.addFilter(pkFieldName, array[i++]);
					if (i == size)
						break;
				}
				return query;
			}
			query = toQuery(table, condition, table.getPkFieldNames());
			return query;
		} finally {
			//			query.setPageIndex(0);
			//			query.setPageSize(2);
		}
	}
	protected static <T> T newInstance(Class<T> clazz) {
		try {
			return clazz.newInstance();
		} catch (InstantiationException e) {
			throw new DbistRuntimeException(e);
		} catch (IllegalAccessException e) {
			throw new DbistRuntimeException(e);
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T select(T data) throws Exception {
		ValueUtils.assertNotNull("data", data);
		Class<T> clazz = (Class<T>) data.getClass();
		Query query = toPkQuery(clazz, data);
		return select(selectList(clazz, query));
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T selectWithLock(T data) throws Exception {
		ValueUtils.assertNotNull("data", data);
		Class<T> clazz = (Class<T>) data.getClass();
		Query query = toPkQuery(clazz, data);
		return select(selectListWithLock(clazz, query));
	}

	@Override
	public <T> T select(Class<T> clazz, Object pkCondition) throws Exception {
		ValueUtils.assertNotNull("clazz", clazz);
		ValueUtils.assertNotNull("pkCondition", pkCondition);
		Query query = toPkQuery(clazz, pkCondition);
		return select(selectList(clazz, query));
	}

	@Override
	public <T> T selectWithLock(Class<T> clazz, Object pkCondition) throws Exception {
		ValueUtils.assertNotNull("clazz", clazz);
		Query query = toPkQuery(clazz, pkCondition);
		return select(selectListWithLock(clazz, query));
	}

	@Override
	public <T> T selectByCondition(Class<T> clazz, Object condition) throws Exception {
		ValueUtils.assertNotNull("clazz", clazz);
		ValueUtils.assertNotNull("condition", condition);
		return select(selectList(clazz, condition));
	}

	@Override
	public <T> T select(String tableName, Object pkCondition, Class<T> requiredType) throws Exception {
		ValueUtils.assertNotNull("tableName", tableName);
		ValueUtils.assertNotNull("pkCondition", pkCondition);
		ValueUtils.assertNotNull("requiredType", requiredType);
		Class<?> clazz = getClass(tableName);
		Query query = toPkQuery(clazz, pkCondition);
		Object obj = select(selectList(clazz, query));
		return ValueUtils.populate(obj, requiredType.newInstance());
	}

	@Override
	public <T> T selectWithLock(String tableName, Object pkCondition, Class<T> requiredType) throws Exception {
		ValueUtils.assertNotNull("tableName", tableName);
		ValueUtils.assertNotNull("pkCondition", pkCondition);
		ValueUtils.assertNotNull("requiredType", requiredType);
		Class<?> clazz = getClass(tableName);
		Query query = toPkQuery(clazz, pkCondition);
		Object obj = selectWithLock(selectList(clazz, query));
		return ValueUtils.populate(obj, requiredType.newInstance());
	}

	@Override
	public <T> T selectByCondition(String tableName, Object condition, Class<T> requiredType) throws Exception {
		ValueUtils.assertNotNull("tableName", tableName);
		ValueUtils.assertNotNull("condition", condition);
		ValueUtils.assertNotNull("requiredType", requiredType);
		Class<?> clazz = getClass(tableName);
		Query query = toPkQuery(clazz, condition);
		Object obj = select(selectList(clazz, query));
		return ValueUtils.populate(obj, requiredType.newInstance());
	}

	@Override
	public <T> T selectByConditionWithLock(String tableName, Object condition, Class<T> requiredType) throws Exception {
		ValueUtils.assertNotNull("tableName", tableName);
		ValueUtils.assertNotNull("condition", condition);
		ValueUtils.assertNotNull("requiredType", requiredType);
		Class<?> clazz = getClass(tableName);
		Query query = toPkQuery(clazz, condition);
		Object obj = selectWithLock(selectList(clazz, query));
		return ValueUtils.populate(obj, requiredType.newInstance());
	}

	@Override
	public <T> T selectByConditionWithLock(Class<T> clazz, Object condition) throws Exception {
		ValueUtils.assertNotNull("clazz", clazz);
		ValueUtils.assertNotNull("condition", condition);
		return select(selectListWithLock(clazz, condition));
	}

	@Override
	public <T> T selectByQl(String sql, Map<String, ?> paramMap, Class<T> requiredType) throws Exception {
		ValueUtils.assertNotNull("query", sql);
		ValueUtils.assertNotNull("requiredType", requiredType);
		return select(selectListByQl(sql, paramMap, requiredType, 0, 2));
	}

	@Override
	public <T> Page<T> selectPage(Class<T> clazz, Query query) throws Exception {
		Page<T> page = new Page<T>();
		page.setIndex(query.getPageIndex());
		page.setSize(query.getPageSize());
		page.setTotalSize(selectSize(clazz, query));
		if (page.getIndex() >= 0 && page.getSize() > 0 && page.getTotalSize() > 0)
			page.setLastIndex((page.getTotalSize() / page.getSize()) - (page.getTotalSize() % page.getSize() == 0 ? 1 : 0));
		page.setList(selectList(clazz, query));
		return page;
	}

	@Override
	public <T> int selectSize(String tableName, Object condition) throws Exception {
		return selectSize(getClass(tableName), condition);
	}

	@Override
	public <T> List<T> selectList(String tableName, Object condition, Class<T> requiredType) throws Exception {
		List<?> objList = selectList(getClass(tableName), condition);
		List<T> list = new ArrayList<T>();
		for (Object obj : objList)
			list.add(ValueUtils.populate(obj, requiredType.newInstance()));
		return list;
	}

	@Override
	public <T> List<T> selectListWithLock(String tableName, Object condition, Class<T> requiredType) throws Exception {
		List<?> objList = selectListWithLock(getClass(tableName), condition);
		List<T> list = new ArrayList<T>();
		for (Object obj : objList)
			list.add(ValueUtils.populate(obj, requiredType.newInstance()));
		return list;
	}

	@Override
	public <T> Page<T> selectPage(String tableName, Query query, Class<T> requiredType) throws Exception {
		Page<T> page = new Page<T>();
		page.setIndex(query.getPageIndex());
		page.setSize(query.getPageSize());
		page.setTotalSize(selectSize(tableName, query));
		if (page.getIndex() >= 0 && page.getSize() > 0 && page.getTotalSize() > 0)
			page.setLastIndex((page.getTotalSize() / page.getSize()) - (page.getTotalSize() % page.getSize() == 0 ? 1 : 0));
		page.setList(selectList(tableName, query, requiredType));
		return page;
	}

	@Override
	public <T> T selectBySql(String sql, Map<String, ?> paramMap, Class<T> requiredType) throws Exception {
		return selectByQl(sql, paramMap, requiredType);
	}

	@Override
	public <T> List<T> selectListBySql(String sql, Map<String, ?> paramMap, Class<T> requiredType, int pageIndex, int pageSize) throws Exception {
		return selectListByQl(sql, paramMap, requiredType, pageIndex, pageSize);
	}

	@Override
	public <T> Page<T> selectPageBySql(String sql, Map<String, ?> paramMap, Class<T> requiredType, int pageIndex, int pageSize) throws Exception {
		return selectPageByQl(sql, paramMap, requiredType, pageIndex, pageSize);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T insert(Class<T> clazz, Object data) throws Exception {
		ValueUtils.assertNotNull("clazz", clazz);
		ValueUtils.assertNotNull("data", data);
		if (data.getClass().isAssignableFrom(clazz)) {
			T obj = (T) data;
			insert(obj);
			return obj;
		}
		T obj = ValueUtils.populate(data, clazz.newInstance());
		insert(obj);
		return obj;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T update(Class<T> clazz, Object data) throws Exception {
		ValueUtils.assertNotNull("clazz", clazz);
		ValueUtils.assertNotNull("data", data);
		if (data.getClass().isAssignableFrom(clazz)) {
			T obj = (T) data;
			update(obj);
		}
		T obj = select(clazz, data);
		if (obj == null) {
			Table table = getTable(data);
			throw new DataNotFoundException("Couldn't find data from table[" + table.getDomain() + "." + table.getName() + "]");
		}
		obj = ValueUtils.populate(data, obj);
		update(obj);
		return obj;
	}

	@Override
	public <T> void upsert(T data) throws Exception {
		if (select(data) == null)
			insert(data);
		else
			update(data);
	}

	@Override
	public <T> void upsertBatch(List<T> list) throws Exception {
		List<T> insertList = new ArrayList<T>();
		List<T> updateList = new ArrayList<T>();
		for (T data : list) {
			if (select(data) == null)
				insertList.add(data);
			else
				updateList.add(data);
		}
		insertBatch(insertList);
		updateBatch(updateList);
	}

	@Override
	public <T> T upsert(Class<T> clazz, Object data) throws Exception {
		return select(clazz, data) == null ? insert(clazz, data) : update(clazz, data);
	}

	@Override
	public <T> T delete(Class<T> clazz, Object condition) throws Exception {
		T data = select(clazz, condition);
		if (data == null)
			return null;
		delete(data);
		return data;
	}
}