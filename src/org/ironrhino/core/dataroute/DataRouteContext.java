package org.ironrhino.core.dataroute;

import lombok.experimental.UtilityClass;

@UtilityClass
public class DataRouteContext {

	public static final int DEFAULT_DATASOURCE_WEIGHT = 1;

	private static ThreadLocal<Object> routingKey = new ThreadLocal<>();

	private static ThreadLocal<String> routerName = new ThreadLocal<>();

	private static ThreadLocal<String> nodeName = new ThreadLocal<>();

	public static void setNodeName(String s) {
		nodeName.set(s);
	}

	public static void removeNodeName(String s) {
		String exists = nodeName.get();
		if (s.equals(exists))
			nodeName.remove();
	}

	static String getNodeName() {
		String s = nodeName.get();
		nodeName.remove();
		return s;
	}

	public static void setRoutingKey(Object obj) {
		routingKey.set(obj);
	}

	public static void removeRoutingKey(Object obj) {
		Object exists = routingKey.get();
		if (obj.equals(exists))
			routingKey.remove();
	}

	static Object getRoutingKey() {
		Object obj = routingKey.get();
		routingKey.remove();
		return obj;
	}

	public static void setRouterName(String s) {
		routerName.set(s);
	}

	public static void removeRouterName(String s) {
		String exists = routerName.get();
		if (s.equals(exists))
			routerName.remove();
	}

	static String getRouterName() {
		String s = routerName.get();
		routerName.remove();
		return s;
	}

}