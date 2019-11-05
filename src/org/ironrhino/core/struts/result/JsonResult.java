package org.ironrhino.core.struts.result;

import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.struts2.ServletActionContext;
import org.ironrhino.core.metadata.JsonConfig;
import org.ironrhino.core.struts.BaseAction;
import org.ironrhino.core.util.JsonUtils;
import org.ironrhino.core.util.ReflectionUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

import com.opensymphony.xwork2.ActionInvocation;
import com.opensymphony.xwork2.Result;
import com.opensymphony.xwork2.ValidationAware;

@Component("org.ironrhino.core.struts.result.JsonResult")
public class JsonResult implements Result {

	private static final long serialVersionUID = 5984356746581381755L;

	private String generateJson(ActionInvocation invocation) {
		Object action = ReflectionUtils.getTargetObject(invocation.getAction());
		Method method = BeanUtils.findDeclaredMethod(action.getClass(), invocation.getProxy().getMethod(),
				new Class[0]);
		if (method == null)
			return "";
		boolean hasErrors = false;
		Map<String, Object> map = new HashMap<>();
		if (action instanceof ValidationAware) {
			ValidationAware validationAwareAction = (ValidationAware) action;
			if (validationAwareAction.hasErrors()) {
				hasErrors = true;
				if (validationAwareAction.hasActionErrors())
					map.put("actionErrors", validationAwareAction.getActionErrors());
				if (validationAwareAction.hasFieldErrors())
					map.put("fieldErrors", validationAwareAction.getFieldErrors());
				return JsonUtils.toJson(map);
			}

			if (validationAwareAction.hasActionMessages())
				map.put("actionMessages", validationAwareAction.getActionMessages());

			if (action instanceof BaseAction) {
				BaseAction baseAction = (BaseAction) action;
				if (StringUtils.isNotBlank(baseAction.getActionWarning()))
					map.put("actionWarning", baseAction.getActionWarning());
				if (StringUtils.isNotBlank(baseAction.getActionSuccessMessage()))
					map.put("actionSuccessMessage", baseAction.getActionSuccessMessage());
			}
		}
		if (!hasErrors) {
			JsonConfig jsonConfig = method.getAnnotation(JsonConfig.class);
			if (jsonConfig != null && StringUtils.isNotBlank(jsonConfig.root())) {
				Object value = invocation.getStack().findValue(jsonConfig.root());
				return value != null ? JsonUtils.toJson(value) : "{}";
			}
			if (jsonConfig == null || jsonConfig.propertyName() == null || jsonConfig.propertyName().length == 0) {
				return JsonUtils.toJson(map);
			}
			String[] propertyNameArray = jsonConfig.propertyName();
			if (propertyNameArray != null && propertyNameArray.length > 0) {
				for (String name : propertyNameArray) {
					Object value = invocation.getStack().findValue(name);
					if (value != null)
						map.put(name, value);
				}
			}
		}
		return JsonUtils.toJson(map);
	}

	@Override
	public void execute(ActionInvocation invocation) throws Exception {
		String json = generateJson(invocation);
		HttpServletResponse response = ServletActionContext.getResponse();
		String encoding = response.getCharacterEncoding();
		response.setContentType("application/json;charset=" + encoding);
		if (!response.containsHeader("Cache-Control")) {
			response.setHeader("Cache-Control", "no-cache");
			response.setHeader("Pragma", "no-cache");
			response.setDateHeader("Expires", 0);
		}
		PrintWriter out = response.getWriter();
		out.print(json);
		out.flush();
		out.close();
	}
}