package org.ironrhino.rest;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ironrhino.core.spring.converter.DateConverter;
import org.ironrhino.core.util.JsonUtils;
import org.ironrhino.rest.component.AuthorizeAspect;
import org.ironrhino.rest.component.JsonpAdvice;
import org.ironrhino.rest.component.RestExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.format.FormatterRegistry;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class ApiConfigBase extends WebMvcConfigurationSupport {

	public static ObjectMapper createObjectMapper() {
		ObjectMapper objectMapper = JsonUtils.createNewObjectMapper();
		objectMapper
				.setDateFormat(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss"));
		objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
		return objectMapper;
	}

	@Bean
	public PropertySourcesPlaceholderConfigurer propertyPlaceholderConfigurer() {
		return new PropertySourcesPlaceholderConfigurer();
	}

	@Override
	protected void configureContentNegotiation(
			ContentNegotiationConfigurer configurer) {
		configurer.defaultContentType(MediaType.APPLICATION_JSON);
		configurer.favorPathExtension(false);
	}

	@Override
	protected void configureMessageConverters(
			List<HttpMessageConverter<?>> converters) {
		MappingJackson2HttpMessageConverter jackson2 = new MappingJackson2HttpMessageConverter();
		jackson2.setObjectMapper(createObjectMapper());
		converters.add(jackson2);
		StringHttpMessageConverter string = new StringHttpMessageConverter();
		string.setWriteAcceptCharset(false);
		converters.add(string);
	}

	@Override
	protected Map<String, MediaType> getDefaultMediaTypes() {
		Map<String, MediaType> map = new HashMap<String, MediaType>();
		map.put("json", MediaType.APPLICATION_JSON);
		map.put("txt", MediaType.TEXT_PLAIN);
		return map;
	}

	@Override
	public void addFormatters(FormatterRegistry formatterRegistry) {
		formatterRegistry.addConverter(new DateConverter());
	}

	@Bean
	protected RestExceptionHandler restExceptionHandler() {
		return new RestExceptionHandler();
	}

	@Bean
	protected JsonpAdvice jsonpAdvice() {
		return new JsonpAdvice();
	}

	@Bean
	protected AuthorizeAspect authorizeAspect() {
		return new AuthorizeAspect();
	}

}