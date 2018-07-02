package org.ironrhino.rest;

import org.apache.commons.lang3.StringUtils;
import org.ironrhino.core.struts.I18N;
import org.springframework.beans.BeanWrapperImpl;

public abstract class Asserts {

	public static void notNull(Object domain, String... field) {
		BeanWrapperImpl bw = new BeanWrapperImpl(domain);
		for (String f : field) {
			Object value = bw.getPropertyValue(f);
			if (value == null)
				throw RestStatus.valueOf(RestStatus.CODE_FIELD_INVALID, f + ": " + I18N.getText("validation.is.null"));
		}
	}

	public static void notBlank(Object domain, String... field) {
		BeanWrapperImpl bw = new BeanWrapperImpl(domain);
		for (String f : field) {
			Object value = bw.getPropertyValue(f);
			String str = null;
			if (value != null)
				str = String.valueOf(value);
			if (StringUtils.isBlank(str))
				throw RestStatus.valueOf(RestStatus.CODE_FIELD_INVALID, f + ": " + I18N.getText("validation.is.blank"));
		}
	}

}
