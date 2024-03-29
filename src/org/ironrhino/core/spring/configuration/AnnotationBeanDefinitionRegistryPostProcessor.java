package org.ironrhino.core.spring.configuration;

import java.lang.annotation.Annotation;
import java.lang.annotation.Inherited;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang3.StringUtils;
import org.ironrhino.core.spring.NameGenerator;
import org.ironrhino.core.util.ClassScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.env.Environment;

import lombok.Getter;
import lombok.Setter;

public abstract class AnnotationBeanDefinitionRegistryPostProcessor<A extends Annotation, FB extends FactoryBean<?>>
		implements BeanDefinitionRegistryPostProcessor, EnvironmentAware {

	protected Logger log = LoggerFactory.getLogger(getClass());

	private final Class<A> annotationClass;

	private final Class<FB> factoryBeanClass;

	@Getter
	@Setter
	private String[] packagesToScan;

	@Getter
	@Setter
	// for unit tests
	private Class<?>[] annotatedClasses;

	protected Environment env;

	@SuppressWarnings("unchecked")
	public AnnotationBeanDefinitionRegistryPostProcessor() {
		ResolvableType rt = ResolvableType.forClass(getClass()).as(AnnotationBeanDefinitionRegistryPostProcessor.class);
		annotationClass = (Class<A>) rt.resolveGeneric(0);
		factoryBeanClass = (Class<FB>) rt.resolveGeneric(1);
	}

	@Override
	public void setEnvironment(Environment env) {
		this.env = env;
	}

	@Override
	public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
		boolean inherited = annotationClass.isAnnotationPresent(Inherited.class);
		Set<Class<?>> classes = new TreeSet<>(Comparator.comparing(Class::getName));
		if (annotatedClasses != null)
			classes.addAll(Arrays.asList(annotatedClasses));
		if (packagesToScan != null)
			classes.addAll(ClassScanner.scanAnnotated(packagesToScan, annotationClass, inherited));
		if (annotatedClasses == null && packagesToScan == null)
			classes.addAll(ClassScanner.scanAnnotated(ClassScanner.getAppPackages(), annotationClass, inherited));
		for (Class<?> annotatedClass : classes) {
			if (!annotatedClass.isInterface() || inherited && annotatedClass.getTypeParameters().length > 0
					|| shouldSkip(annotatedClass))
				continue;
			String key = annotatedClass.getName() + ".imported";
			if ("false".equals(env.getProperty(key))) {
				log.info("Skipped import interface [{}] because {}=false", annotatedClass.getName(), key);
				continue;
			}
			A annotation = AnnotatedElementUtils.findMergedAnnotation(annotatedClass, annotationClass);
			String beanName = getExplicitBeanName(annotation);
			if (StringUtils.isBlank(beanName))
				beanName = NameGenerator.buildDefaultBeanName(annotatedClass.getName());
			if (registry.containsBeanDefinition(beanName)) {
				BeanDefinition bd = registry.getBeanDefinition(beanName);
				if (existsBean(beanName, annotatedClass, bd, registry)) {
					String beanClassName = bd.getBeanClassName();
					log.info("Skipped import interface [{}] because bean[{}#{}] exists", annotatedClass.getName(),
							beanClassName, beanName);
					continue;
				}
				beanName = annotatedClass.getName();
			}
			RootBeanDefinition beanDefinition = new RootBeanDefinition(factoryBeanClass);
			beanDefinition.setPrimary(true);
			beanDefinition.setTargetType(annotatedClass);
			beanDefinition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_NO);
			beanDefinition.setAttribute(getClass().getName(), true);
			processBeanDefinition(annotation, annotatedClass, beanDefinition);
			registry.registerBeanDefinition(beanName, beanDefinition);
			log.info("Register bean [{}] for @{} [{}]", beanName, annotationClass.getSimpleName(),
					annotatedClass.getName());
		}
	}

	protected boolean shouldSkip(Class<?> annotatedClass) {
		return false;
	}

	private boolean existsBean(String beanName, Class<?> annotatedClass, BeanDefinition bd,
			BeanDefinitionRegistry registry) {
		String beanClassName = bd.getBeanClassName();
		Class<?> beanClass = null;
		try {
			if (beanClassName != null) {
				beanClass = Class.forName(beanClassName);
				if (FactoryBean.class.isAssignableFrom(beanClass)) {
					if (ResolvableType.forClassWithGenerics(FactoryBean.class, annotatedClass)
							.isAssignableFrom(beanClass))
						return true;
				}
			} else if (bd instanceof RootBeanDefinition) {
				RootBeanDefinition rbd = (RootBeanDefinition) bd;
				String fbn = rbd.getFactoryBeanName();
				if (fbn != null) {
					BeanDefinition fbd = registry.getBeanDefinition(fbn);
					String factoryBeanClassName = fbd.getBeanClassName();
					if (factoryBeanClassName != null) {
						Class<?> factoryBeanClass = Class.forName(factoryBeanClassName);
						String factoryMethodName = rbd.getFactoryMethodName();
						if (factoryMethodName != null) {
							Method m = org.springframework.util.ReflectionUtils.findMethod(factoryBeanClass,
									factoryMethodName);
							if (m != null) {
								if (m.isAnnotationPresent(Fallback.class))
									return false;
								beanClass = m.getReturnType();
								beanClassName = beanClass.getName();
							}
						}
					}
				}
			} else {
				return false;
			}
		} catch (ClassNotFoundException e) {
			log.error(e.getMessage(), e);
		}
		if (beanClass == null || annotatedClass.isAssignableFrom(beanClass)
				|| ResolvableType.forClassWithGenerics(FactoryBean.class, annotatedClass).isAssignableFrom(beanClass)
				|| !bd.hasAttribute(getClass().getName()) && beanClass.equals(factoryBeanClass)) {
			return true;
		}
		if (!beanClass.isAnnotationPresent(Fallback.class)) {
			if (annotatedClass.isAssignableFrom(beanClass)) {
				return true;
			}
			if (bd instanceof RootBeanDefinition && FactoryBean.class.isAssignableFrom(beanClass)) {
				Class<?> targetType = ((RootBeanDefinition) bd).getTargetType();
				if (targetType != null && annotatedClass.isAssignableFrom(targetType)) {
					beanClassName = targetType.getName();
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {

	}

	protected abstract void processBeanDefinition(A annotation, Class<?> annotatedClass,
			RootBeanDefinition beanDefinition);

	protected abstract String getExplicitBeanName(A annotation);
}