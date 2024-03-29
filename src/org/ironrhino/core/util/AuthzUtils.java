package org.ironrhino.core.util;

import java.util.Collection;
import java.util.Collections;

import org.apache.commons.lang3.StringUtils;
import org.ironrhino.core.model.Secured;
import org.ironrhino.core.security.role.UserRole;
import org.ironrhino.core.servlet.RequestContext;
import org.slf4j.MDC;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.BeansException;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.security.access.expression.SecurityExpressionRoot;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

import lombok.experimental.UtilityClass;

@UtilityClass
public class AuthzUtils {

	public static final ThreadLocal<UserDetails> DOUBLE_CHCKER_HOLDER = new ThreadLocal<>();

	private static ExpressionParser expressionParser = new SpelExpressionParser();

	public static Object authentication(String property) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null)
			return null;
		if (auth.getPrincipal() == null)
			return null;
		try {
			BeanWrapperImpl wrapper = new BeanWrapperImpl(auth);
			return wrapper.getPropertyValue(property);
		} catch (BeansException e) {
			return null;
		}
	}

	public static boolean authorize(String access) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null)
			return false;
		Boolean b = expressionParser.parseExpression(access).getValue(new WebSecurityExpressionRoot(auth),
				Boolean.class);
		return b != null && b;
	}

	public static boolean authorizeRoles(Collection<String> roles, String access) {
		Authentication auth = new TestingAuthenticationToken(null, null, roles.toArray(new String[0]));
		Boolean b = expressionParser.parseExpression(access).getValue(new WebSecurityExpressionRoot(auth),
				Boolean.class);
		return b != null && b;
	}

	public static boolean authorize(String ifAllGranted, String ifAnyGranted, String ifNotGranted) {
		return authorizeRoles(getRoleNames(), ifAllGranted, ifAnyGranted, ifNotGranted);
	}

	public static boolean authorize(String[] ifAllGranted, String[] ifAnyGranted, String[] ifNotGranted) {
		return authorizeRoles(getRoleNames(), ifAllGranted, ifAnyGranted, ifNotGranted);
	}

	// equals to authorize, for ftl call
	public static boolean authorizeArray(String[] ifAllGranted, String[] ifAnyGranted, String[] ifNotGranted) {
		return authorize(ifAllGranted, ifAnyGranted, ifNotGranted);
	}

	public static boolean authorizeUserDetails(UserDetails user, String ifAllGranted, String ifAnyGranted,
			String ifNotGranted) {
		return authorizeRoles(getRoleNamesFromUserDetails(user), ifAllGranted, ifAnyGranted, ifNotGranted);
	}

	public static boolean authorizeUserDetails(UserDetails user, String[] ifAllGranted, String[] ifAnyGranted,
			String[] ifNotGranted) {
		return authorizeRoles(getRoleNamesFromUserDetails(user), ifAllGranted, ifAnyGranted, ifNotGranted);
	}

	public static boolean authorizeRoles(Collection<String> roles, String ifAllGranted, String ifAnyGranted,
			String ifNotGranted) {
		if (StringUtils.isNotBlank(ifAllGranted)) {
			String[] arr = ifAllGranted.split("\\s*,\\s*");
			for (String s : arr)
				if (!roles.contains(s.trim()))
					return false;
			return true;
		} else if (StringUtils.isNotBlank(ifAnyGranted)) {
			String[] arr = ifAnyGranted.split("\\s*,\\s*");
			for (String s : arr)
				if (roles.contains(s.trim()))
					return true;
			return false;
		} else if (StringUtils.isNotBlank(ifNotGranted)) {
			String[] arr = ifNotGranted.split("\\s*,\\s*");
			boolean b = true;
			for (String s : arr)
				if (roles.contains(s.trim())) {
					b = false;
					break;
				}
			return b;
		}
		return false;
	}

	public static boolean authorizeRoles(Collection<String> roles, String[] ifAllGranted, String[] ifAnyGranted,
			String[] ifNotGranted) {
		if (ifAllGranted != null && ifAllGranted.length > 0) {
			for (String s : ifAllGranted) {
				String[] arr = s.split("\\s*,\\s*");
				for (String ss : arr)
					if (!roles.contains(ss.trim()))
						return false;
			}
			return true;
		} else if (ifAnyGranted != null && ifAnyGranted.length > 0) {
			for (String s : ifAnyGranted) {
				String[] arr = s.split("\\s*,\\s*");
				for (String ss : arr)
					if (roles.contains(ss.trim()))
						return true;
			}
			return false;
		} else if (ifNotGranted != null && ifNotGranted.length > 0) {
			boolean b = true;
			label: for (String s : ifNotGranted) {
				String[] arr = s.split("\\s*,\\s*");
				for (String ss : arr)
					if (roles.contains(ss.trim())) {
						b = false;
						break label;
					}
			}
			return b;
		}
		return false;
	}

	public static Collection<String> getRoleNames() {
		if (SecurityContextHolder.getContext().getAuthentication() != null) {
			return AuthorityUtils
					.authorityListToSet(SecurityContextHolder.getContext().getAuthentication().getAuthorities());
		} else
			return Collections.singleton(UserRole.ROLE_BUILTIN_ANONYMOUS);
	}

	public static Collection<String> getRoleNamesFromUserDetails(UserDetails user) {
		return AuthorityUtils.authorityListToSet(user.getAuthorities());
	}

	public static boolean hasRole(String role) {
		return getRoleNames().contains(role);
	}

	public static boolean hasPermission(Secured entity) {
		return hasPermission(entity, false);
	}

	public static boolean hasPermission(Secured entity, boolean defaultWhenEmpty) {
		if (entity == null)
			return false;
		if (entity.getRoles() == null || entity.getRoles().size() == 0)
			return defaultWhenEmpty;
		Collection<String> roleNames = getRoleNames();
		for (String s : entity.getRoles()) {
			if (roleNames.contains(s))
				return true;
		}
		return false;
	}

	public static String getUsername() {
		SecurityContext sc = SecurityContextHolder.getContext();
		if (sc == null)
			return null;
		Authentication auth = sc.getAuthentication();
		if (auth == null || auth instanceof AnonymousAuthenticationToken)
			return null;
		return auth.getName();
	}

	@SuppressWarnings("unchecked")
	public static <T extends UserDetails> T getUserDetails() {
		SecurityContext sc = SecurityContextHolder.getContext();
		if (sc != null) {
			Authentication auth = sc.getAuthentication();
			if (auth != null) {
				Object principal = auth.getPrincipal();
				return principal instanceof UserDetails ? (T) principal : null;
			}
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	public static <T extends UserDetails> T getUserDetails(Class<T> clazz) {
		UserDetails ud = getUserDetails();
		return ud != null && clazz.isInstance(ud) ? (T) ud : null;
	}

	public static void autoLogin(UserDetails ud) {
		if (!ud.isEnabled())
			throw new DisabledException(ud.getUsername());
		else if (!ud.isAccountNonExpired())
			throw new AccountExpiredException(ud.getUsername());
		else if (!ud.isAccountNonLocked())
			throw new LockedException(ud.getUsername());
		SecurityContext sc = SecurityContextHolder.getContext();
		Authentication auth = new UsernamePasswordAuthenticationToken(ud, ud.getPassword(), ud.getAuthorities());
		sc.setAuthentication(auth);
		MDC.put("username", auth.getName());
		if (RequestContext.getRequest() != null) // for pageView uu
			RequestUtils.saveCookie(RequestContext.getRequest(), RequestContext.getResponse(), "UU", ud.getUsername());
	}

	public static String encodePassword(UserDetails ud, String input) {
		PasswordEncoder encoder = ApplicationContextUtils.getBean(PasswordEncoder.class);
		return encoder.encode(input);
	}

	public static boolean isPasswordValid(UserDetails ud, String password) {
		PasswordEncoder encoder = ApplicationContextUtils.getBean(PasswordEncoder.class);
		return encoder.matches(password, ud.getPassword());
	}

	public static boolean isPasswordValid(String password) {
		Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		return principal instanceof UserDetails && isPasswordValid((UserDetails) principal, password);
	}

	@SuppressWarnings("unchecked")
	public static <T extends UserDetails> T getDoubleChecker(Class<T> clazz) {
		UserDetails ud = DOUBLE_CHCKER_HOLDER.get();
		if (ud == null)
			return null;
		if (clazz.isInstance(ud))
			return (T) ud;
		return null;
	}

	// org.springframework.security.web.access.expression.WebSecurityExpressionRoot
	static class WebSecurityExpressionRoot extends SecurityExpressionRoot {
		public WebSecurityExpressionRoot(Authentication authentication) {
			super(authentication);
			setDefaultRolePrefix("");
			setTrustResolver(new AuthenticationTrustResolverImpl());
		}

		public boolean hasIpAddress(String ipAddress) {
			if (RequestContext.getRequest() == null)
				return false;
			return (new IpAddressMatcher(ipAddress).matches(RequestContext.getRequest()));
		}

	}

}