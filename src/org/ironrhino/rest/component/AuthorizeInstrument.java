package org.ironrhino.rest.component;

import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.ironrhino.core.aop.BaseAspect;
import org.ironrhino.core.metadata.Authorize;
import org.ironrhino.core.util.AuthzUtils;
import org.ironrhino.rest.RestStatus;
import org.springframework.core.Ordered;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.RestController;

@Aspect
@ControllerAdvice
public class AuthorizeInstrument extends BaseAspect {

	public AuthorizeInstrument() {
		order = Ordered.HIGHEST_PRECEDENCE + 1;
	}

	@Before("execution(public * *(..)) and @within(restController) and not @annotation(org.ironrhino.core.metadata.Authorize)")
	public void authorizeClass(JoinPoint jp, RestController restController) {
		authorize(jp.getTarget().getClass().getAnnotation(Authorize.class));
	}

	@Before("execution(public * *(..)) and @within(restController) and @annotation(authorize)")
	public void authorizeMethod(JoinPoint jp, RestController restController, Authorize authorize) {
		authorize(authorize);
	}

	private void authorize(Authorize authorize) {
		if (authorize != null) {
			if (StringUtils.isNotBlank(authorize.access()) && !AuthzUtils.authorize(authorize.access())) {
				throw RestStatus.UNAUTHORIZED;
			} else if (!AuthzUtils.authorize(authorize.ifAllGranted(), authorize.ifAnyGranted(),
					authorize.ifNotGranted())) {
				throw RestStatus.UNAUTHORIZED;
			}
		}
	}

}
