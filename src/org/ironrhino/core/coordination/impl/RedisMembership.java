package org.ironrhino.core.coordination.impl;

import static org.ironrhino.core.metadata.Profiles.CLOUD;
import static org.ironrhino.core.metadata.Profiles.DUAL;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import javax.annotation.PreDestroy;

import org.ironrhino.core.coordination.Membership;
import org.ironrhino.core.spring.configuration.PriorityQualifier;
import org.ironrhino.core.spring.configuration.ServiceImplementationConditional;
import org.ironrhino.core.util.AppInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

@Component("membership")
@ServiceImplementationConditional(profiles = { DUAL, CLOUD })
public class RedisMembership implements Membership, SchedulingConfigurer {
	private static final String NAMESPACE = "membership:";

	private Set<String> groups = Collections.newSetFromMap(new ConcurrentHashMap<>());

	@Value("${redis.membership.heartbeat:60000}")
	private int heartbeat = 60000;

	@Autowired
	@Qualifier("stringRedisTemplate")
	@PriorityQualifier
	private StringRedisTemplate coordinationStringRedisTemplate;

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public void join(String group) {
		String key = NAMESPACE + group;
		String self = AppInfo.getInstanceId();
		coordinationStringRedisTemplate.executePipelined((SessionCallback) redisOperations -> {
			redisOperations.opsForList().remove(key, 1, self);
			redisOperations.opsForList().leftPush(key, self);
			return null;
		});
		groups.add(group);
	}

	@Override
	public void leave(String group) {
		coordinationStringRedisTemplate.opsForList().remove(NAMESPACE + group, 0, AppInfo.getInstanceId());
		groups.remove(group);
	}

	@Override
	public boolean isLeader(String group) {
		return AppInfo.getInstanceId().equals(getLeader(group));
	}

	@Override
	public String getLeader(String group) {
		List<String> list = getMembers(group);
		if (!list.isEmpty())
			return list.get(0);
		return null;
	}

	@Override
	public List<String> getMembers(String group) {
		return coordinationStringRedisTemplate.opsForList().range(NAMESPACE + group, 0, -1);
	}

	@PreDestroy
	public void destroy() {
		for (String group : groups)
			coordinationStringRedisTemplate.opsForList().remove(NAMESPACE + group, 0, AppInfo.getInstanceId());
	}

	@Override
	public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
		if (AppInfo.getContextPath() != null) // in container
			taskRegistrar.addFixedDelayTask(this::doHeartbeat, heartbeat);
	}

	private void doHeartbeat() {

		for (String group : groups) {
			List<String> members = getMembers(group);
			String self = AppInfo.getInstanceId();
			if (!members.contains(self))
				coordinationStringRedisTemplate.opsForList().rightPush(NAMESPACE + group, self);
			for (String member : members) {
				if (member.equals(self))
					continue;
				boolean alive = false;
				String url = "http://" + member.substring(member.lastIndexOf('@') + 1) + "/_ping?_internal_testing_";
				try {
					HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
					conn.setConnectTimeout(3000);
					conn.setReadTimeout(2000);
					conn.setInstanceFollowRedirects(false);
					conn.setDoOutput(false);
					conn.setUseCaches(false);
					conn.connect();
					if (conn.getResponseCode() == 200) {
						try (BufferedReader br = new BufferedReader(
								new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
							String value = br.lines().collect(Collectors.joining("\n"));
							if (value.equals(member)) {
								alive = true;
							} else {
								if (!members.contains(value) && value.length() <= 100
										&& value.matches("[\\w-]+@[\\w.:]+")) {
									if (AppInfo.getAppName().equals(value.substring(0, value.lastIndexOf('-')))) {
										coordinationStringRedisTemplate.opsForList().rightPush(NAMESPACE + group,
												value);
									} else {
										// multiple virtual host
										alive = true;
									}
								}
							}
						}
					}
					conn.disconnect();
				} catch (IOException e) {
				}
				if (!alive)
					coordinationStringRedisTemplate.opsForList().remove(NAMESPACE + group, 0, member);
			}
		}

	}

}
