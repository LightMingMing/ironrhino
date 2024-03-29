package org.ironrhino.core.servlet.handles;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.ironrhino.core.servlet.AccessHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FirewallHandler extends AccessHandler {

	public static final String KEY_ALLOWEDADDRPATTERN = "firewallHandler.allowedAddrPattern";

	@Getter
	@Setter
	@Value("${" + KEY_ALLOWEDADDRPATTERN + ":}")
	private String allowedAddrPattern;

	@Override
	public boolean handle(HttpServletRequest request, HttpServletResponse response) {
		String addr = request.getRemoteAddr();
		if (addr.equals("127.0.0.1") || addr.equals("0:0:0:0:0:0:0:1")) {
			String value = request.getParameter(KEY_ALLOWEDADDRPATTERN);
			if (value != null) {
				if (!"true".equalsIgnoreCase(request.getParameter("readonly")))
					this.allowedAddrPattern = value;
				response.setContentType("text/plain");
				try {
					response.getWriter().write(KEY_ALLOWEDADDRPATTERN + '=' + this.allowedAddrPattern);
				} catch (IOException e) {
					e.printStackTrace();
				}
				return true;
			}
			return false;
		} else if (!isAllowed(addr, allowedAddrPattern)) {
			response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
			return true;
		} else {
			return false;
		}
	}

	private static boolean isAllowed(String addr, String allow) {
		if (StringUtils.isBlank(allow))
			return true;
		String[] arr = allow.split("\\s*,\\s*");
		for (String s : arr) {
			if (org.ironrhino.core.util.StringUtils.matchesWildcard(addr, s))
				return true;
		}
		return false;
	}

}
