package org.ironrhino.rest.client;

import java.util.Collection;

import org.apache.commons.lang3.StringUtils;
import org.ironrhino.core.spring.NameGenerator;
import org.ironrhino.core.util.ClassScanner;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class RestApiRegistryPostProcessor implements BeanDefinitionRegistryPostProcessor {

	@Getter
	@Setter
	private String[] packagesToScan;

	@Override
	public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
		Collection<Class<?>> restApiClasses = ClassScanner
				.scanAnnotated(packagesToScan != null ? packagesToScan : ClassScanner.getAppPackages(), RestApi.class);
		for (Class<?> restApiClass : restApiClasses) {
			if (!restApiClass.isInterface())
				continue;
			RestApi annotation = AnnotatedElementUtils.getMergedAnnotation(restApiClass, RestApi.class);
			if (annotation == null)
				continue;
			String beanName = annotation.name();
			if (StringUtils.isBlank(beanName)) {
				beanName = NameGenerator.buildDefaultBeanName(restApiClass.getName());
				if (registry.containsBeanDefinition(beanName))
					beanName = restApiClass.getName();
			}
			RootBeanDefinition beanDefinition = new RootBeanDefinition(RestApiFactoryBean.class);
			beanDefinition.setTargetType(restApiClass);
			beanDefinition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_NO);
			ConstructorArgumentValues constructorArgumentValues = new ConstructorArgumentValues();
			constructorArgumentValues.addIndexedArgumentValue(0, restApiClass);
			if (StringUtils.isNotBlank(annotation.restClient()))
				constructorArgumentValues.addIndexedArgumentValue(1, new RuntimeBeanReference(annotation.restClient()));
			else if (StringUtils.isNotBlank(annotation.restTemplate()))
				constructorArgumentValues.addIndexedArgumentValue(1,
						new RuntimeBeanReference(annotation.restTemplate()));
			beanDefinition.setConstructorArgumentValues(constructorArgumentValues);
			registry.registerBeanDefinition(beanName, beanDefinition);
			log.info("Register bean [{}] for @RestApi [{}]", beanName, restApiClass.getName());
		}
	}

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {

	}
}