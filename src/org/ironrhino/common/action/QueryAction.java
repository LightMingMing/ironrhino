package org.ironrhino.common.action;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.struts2.ServletActionContext;
import org.ironrhino.core.jdbc.JdbcQueryService;
import org.ironrhino.core.jdbc.LineHandler;
import org.ironrhino.core.jdbc.QueryCriteria;
import org.ironrhino.core.jdbc.SqlUtils;
import org.ironrhino.core.metadata.Authorize;
import org.ironrhino.core.metadata.AutoConfig;
import org.ironrhino.core.model.ResultPage;
import org.ironrhino.core.security.role.UserRole;
import org.ironrhino.core.struts.BaseAction;
import org.ironrhino.core.util.ErrorMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import lombok.Getter;
import lombok.Setter;

@AutoConfig
@Authorize(ifAnyGranted = UserRole.ROLE_ADMINISTRATOR)
public class QueryAction extends BaseAction {

	private static final long serialVersionUID = 8180265410790553918L;

	@Getter
	@Setter
	protected String sql;

	@Getter
	protected Map<String, String> params;

	@Getter
	protected List<String> tables;

	@Getter
	@Setter
	protected Map<String, String> paramMap = new HashMap<>();

	@Getter
	@Setter
	protected ResultPage<Map<String, Object>> resultPage;

	@Value("${query.result.maxSize:100000}")
	private int resultMaxSize = 100000;

	@Value("${csv.defaultEncoding:GBK}")
	private String csvDefaultEncoding = "GBK";

	@Autowired(required = false)
	protected JdbcQueryService jdbcQueryService;

	@Override
	public String execute() {
		if (jdbcQueryService == null)
			return NOTFOUND;
		tables = jdbcQueryService.getTables();
		if (StringUtils.isNotBlank(sql)) {
			params = SqlUtils.extractParametersWithType(sql, jdbcQueryService.getDataSource());
			if (params.size() > 0) {
				boolean ready = ServletActionContext.getRequest().getMethod().equalsIgnoreCase("POST");
				if (ready) {
					for (String s : params.keySet()) {
						if (!paramMap.containsKey(s)) {
							ready = false;
							break;
						}
					}
				}
				if (!ready) {
					jdbcQueryService.validate(sql);
					return SUCCESS;
				}
			}
			if (resultPage == null) {
				resultPage = new ResultPage<>();
			}
			resultPage.setCriteria(new QueryCriteria(sql, SqlUtils.convertParameters(paramMap, params)));
			resultPage = jdbcQueryService.query(resultPage);
		}
		return SUCCESS;
	}

	public String export() throws Exception {
		if (jdbcQueryService == null)
			return NOTFOUND;
		if (StringUtils.isNotBlank(sql)) {
			jdbcQueryService.validate(sql);
			params = SqlUtils.extractParametersWithType(sql, jdbcQueryService.getDataSource());
			if (params.size() > 0) {
				for (String s : params.keySet()) {
					if (!paramMap.containsKey(s)) {
						return SUCCESS;
					}
				}
			}
			Map<String, Object> actualParams = SqlUtils.convertParameters(paramMap, params);
			long count = jdbcQueryService.count(sql, actualParams);
			if (count > resultMaxSize)
				throw new ErrorMessage("query.result.size.exceed", new Object[] { resultMaxSize });
			HttpServletResponse response = ServletActionContext.getResponse();
			response.setCharacterEncoding(csvDefaultEncoding);
			response.setHeader("Content-type", "text/csv");
			response.setHeader("Content-disposition", "attachment;filename=data.csv");
			final PrintWriter writer = response.getWriter();
			jdbcQueryService.query(sql, actualParams, new LineHandler() {
				@Override
				public boolean isWithHeader() {
					return true;
				}

				@Override
				public String getColumnSeperator() {
					return ",";
				}

				@Override
				public String getLineSeperator() {
					return "\r\n";
				}

				@Override
				public void handleLine(int index, String line) {
					writer.write(line);
					writer.write(getLineSeperator());
					if (index > 0 && index % 100 == 0)
						writer.flush();
				}
			});
			writer.close();
		}
		return NONE;
	}
}
