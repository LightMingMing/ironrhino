package org.ironrhino.core.spring.security;

import java.io.IOException;

import javax.annotation.PostConstruct;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ironrhino.core.event.EventPublisher;
import org.ironrhino.core.metadata.Scope;
import org.ironrhino.core.security.event.LogoutEvent;
import org.ironrhino.core.util.RequestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.AbstractAuthenticationTargetUrlRequestHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import lombok.Getter;
import lombok.Setter;

@Component
@SpringSecurityEnabled
public class DefaultLogoutSuccessHandler extends AbstractAuthenticationTargetUrlRequestHandler
		implements LogoutSuccessHandler {

	@Autowired
	private transient EventPublisher eventPublisher;

	@Value("${logout.defaultTargetUrl:/}")
	private String defaultTargetUrl;

	@PostConstruct
	public void afterPropertiesSet() {
		setDefaultTargetUrl(defaultTargetUrl);
	}

	@Getter
	@Setter
	private boolean useReferer;

	@Override
	protected String determineTargetUrl(HttpServletRequest request, HttpServletResponse response) {
		String targetUrl = getDefaultTargetUrl();
		if (StringUtils.hasText(request.getParameter("targetUrl"))) {
			targetUrl = request.getParameter("targetUrl");
		} else if (useReferer && request.getParameter("referer") != null) {
			String temp = request.getHeader("Referer");
			if (StringUtils.hasText(temp))
				targetUrl = temp;
		}
		if (targetUrl.startsWith("/"))
			targetUrl = RequestUtils.getBaseUrl(request) + targetUrl;
		return targetUrl;
	}

	@Override
	public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
			throws IOException, ServletException {
		super.handle(request, response, authentication);
		if (authentication != null) {
			Object principal = authentication.getPrincipal();
			if (principal instanceof UserDetails)
				eventPublisher.publish(
						new LogoutEvent(((UserDetails) principal).getUsername(), request.getRemoteAddr()), Scope.LOCAL);
		}
	}

}
