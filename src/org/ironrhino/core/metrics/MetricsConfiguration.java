package org.ironrhino.core.metrics;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.servlet.ServletContext;
import javax.sql.DataSource;

import org.ironrhino.core.util.AppInfo;
import org.ironrhino.core.util.ReflectionUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;

import com.zaxxer.hikari.HikariDataSource;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;

@Order(0)
@Configuration
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
@MicrometerPresent
public class MetricsConfiguration {

	@Autowired(required = false)
	private List<MeterBinder> meterBinders = Collections.emptyList();

	@Autowired(required = false)
	private List<MeterRegistryProvider> meterRegistryProviders = Collections.emptyList();

	@Autowired(required = false)
	private ServletContext servletContext;

	@PostConstruct
	public void init() {
		meterRegistryProviders.forEach(p -> p.get().ifPresent(Metrics::addRegistry));
		if (Metrics.globalRegistry.getRegistries().isEmpty()) {
			org.ironrhino.core.metrics.Metrics.disable();
			return;
		}
		instrument();
	}

	@PreDestroy
	public void destroy() {
		Metrics.globalRegistry.getRegistries().forEach(MeterRegistry::close);
	}

	@Bean
	public TimedAspect timedAspect() {
		return new TimedAspect();
	}

	@Bean
	protected static BeanPostProcessor metricsBeanPostProcessor(Environment env) {
		Metrics.globalRegistry.config().commonTags("app", AppInfo.getAppName(), "instance",
				AppInfo.getInstanceId(true, true));
		return new BeanPostProcessor() {
			@Override
			public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
				if (bean instanceof DataSource) {
					DataSource dataSource = (DataSource) bean;
					try {
						if (dataSource.isWrapperFor(HikariDataSource.class))
							dataSource.unwrap(HikariDataSource.class).setMetricRegistry(Metrics.globalRegistry);
					} catch (SQLException ignored) {
					}
				}
				return bean;
			}
		};
	}

	protected void instrument() {

		MeterRegistry meterRegistry = Metrics.globalRegistry;

		new JvmThreadMetrics().bindTo(meterRegistry);
		new JvmMemoryMetrics().bindTo(meterRegistry);
		meterBinders.forEach(mb -> mb.bindTo(meterRegistry));

		if (servletContext != null) {
			String className = servletContext.getClass().getName();
			if (className.startsWith("org.apache.catalina.")) {
				TomcatMetrics.monitor(meterRegistry);
			} else if (className.startsWith("org.eclipse.jetty.")) {
				try {
					Object webAppContext = ReflectionUtils.getFieldValue(servletContext, "this$0");
					Object server = ReflectionUtils.getFieldValue(webAppContext, "_server");
					Object handler = server.getClass().getMethod("getHandler").invoke(server);
					if (handler.getClass().getSimpleName().equals("StatisticsHandler")) {
						JettyMetrics.monitor(meterRegistry, handler);
					}
				} catch (Throwable e) {
				}
			}
		}
	}

}
