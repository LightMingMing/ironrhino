package org.ironrhino.common.support;

import java.time.LocalDateTime;
import java.util.Map;

import org.hibernate.query.Query;
import org.ironrhino.common.model.BasePollingEntity;
import org.ironrhino.common.model.PollingStatus;
import org.ironrhino.core.metrics.Metrics;
import org.ironrhino.core.util.ExceptionUtils;

public abstract class BasePollingControl<T extends BasePollingEntity> extends AbstractPollingControl<T> {

	@Override
	protected void doDequeue() {
		entityManager.setEntityClass(entityClass);
		while (true) {
			Long id = pop();
			if (id == null)
				break;
			T entity = entityManager.get(id);
			if (entity == null) {
				logger.warn("not found: {}", id);
				continue;
			}
			logger.info("dequeue {}", entity.getId());
			if (entity.getStatus() == PollingStatus.SUCCESSFUL || entity.getStatus() == PollingStatus.FAILED) {
				logger.warn("status is {}: {}", entity.getStatus(), entity.getId());
				continue;
			}
			if (entity.getAttempts() >= getMaxAttempts()) {
				logger.error("max attempts reached: {}", entity.getId());
				entityManager.executeUpdate(simpleUpdateStatusHql, entity.getId(), entity.getStatus(),
						PollingStatus.FAILED, LocalDateTime.now(), "max attempts reached");
				continue;
			}
			try {
				Map<String, Object> fields = Metrics.recordTimer("polling." + entityClass.getName(),
						() -> handle(entity), "batch", "false");
				StringBuilder sb = new StringBuilder("update ");
				sb.append(entityClass.getSimpleName());
				sb.append(" t set t.status=?3,t.modifyDate=?4,t.errorInfo=null,t.attempts=t.attempts+1");
				int i = 5;
				for (String field : fields.keySet())
					sb.append(",t.").append(field).append("=?").append(i++);
				sb.append(" where t.id=?1 and t.status=?2");
				final String hql = sb.toString();
				entityManager.execute(session -> {
					@SuppressWarnings("rawtypes")
					Query query = session.createQuery(hql);
					query.setParameter(1, entity.getId());
					query.setParameter(2, entity.getStatus());
					query.setParameter(3, PollingStatus.SUCCESSFUL);
					query.setParameter(4, LocalDateTime.now());
					int index = 5;
					for (String field : fields.keySet())
						query.setParameter(index++, fields.get(field));
					int result = query.executeUpdate();
					if (result == 1) {
						afterUpdated(session, entity);
						logger.info("process {} successful", entity.getId());
					} else {
						logger.warn("process {} successful but ignored", entity.getId());
					}
					return result;
				});
			} catch (Exception e) {
				logger.error(e.getMessage(), e);
				String errorInfo = ExceptionUtils.getDetailMessage(e);
				if (errorInfo.length() > 4000)
					errorInfo = errorInfo.substring(0, 4000);
				boolean retryable = isTemporaryError(e);
				int result;
				if (retryable) {
					result = entityManager.executeUpdate(conditionalUpdateStatusHql, entity.getId(), entity.getStatus(),
							getMaxAttempts(), PollingStatus.FAILED, PollingStatus.TEMPORARY_ERROR, LocalDateTime.now(),
							errorInfo);
				} else {
					result = entityManager.executeUpdate(defaultUpdateStatusHql, entity.getId(), entity.getStatus(),
							PollingStatus.FAILED, LocalDateTime.now(), errorInfo);
				}
				if (result == 1)
					logger.info("process {} failed", entity.getId());
				else
					logger.warn("process {} failed but ignored", entity.getId());
			}
		}
	}

	protected abstract Map<String, Object> handle(T entity) throws Exception;

}
