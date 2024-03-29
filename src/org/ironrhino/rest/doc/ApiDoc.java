package org.ironrhino.rest.doc;

import java.io.InputStream;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.ironrhino.core.metadata.Authorize;
import org.ironrhino.core.model.ResultPage;
import org.ironrhino.core.util.GenericTypeResolver;
import org.ironrhino.core.util.ReflectionUtils;
import org.ironrhino.rest.RestResult;
import org.ironrhino.rest.doc.annotation.Api;
import org.ironrhino.rest.doc.annotation.Field;
import org.ironrhino.rest.doc.annotation.Fields;
import org.ironrhino.rest.doc.annotation.Status;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJacksonValue;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.async.DeferredResult;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;

import lombok.Data;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Data
public class ApiDoc implements Serializable {

	private static final long serialVersionUID = -3039539795219938302L;

	private String name;

	private String description;

	protected String[] requiredAuthorities;

	protected String url;

	protected String[] methods;

	protected String[] consumes;

	protected String[] produces;

	protected List<FieldObject> pathVariables = new ArrayList<>();

	protected List<FieldObject> requestParams = new ArrayList<>();

	protected List<FieldObject> requestHeaders = new ArrayList<>();

	protected List<FieldObject> cookieValues = new ArrayList<>();

	protected List<FieldObject> requestBody;

	protected List<FieldObject> responseBody;

	protected boolean download;

	protected boolean requestBodyRequired = true;

	protected String requestBodyType;

	protected String responseBodyType;

	protected String requestBodySample;

	protected String responseBodySample;

	protected List<StatusObject> statuses;

	public ApiDoc(Class<?> apiDocClazz, Method apiDocMethod, ObjectMapper objectMapper) throws Exception {
		Class<?> clazz = apiDocClazz.getSuperclass();
		Method method;
		if (clazz != null) {
			try {
				method = clazz.getMethod(apiDocMethod.getName(), apiDocMethod.getParameterTypes());
			} catch (NoSuchMethodException e) {
				// @ApiModule on Controller directly
				clazz = apiDocClazz;
				method = apiDocMethod;
			}
		} else {
			clazz = apiDocClazz;
			method = apiDocMethod;
		}

		Api api = AnnotationUtils.findAnnotation(apiDocMethod, Api.class);
		if (api == null)
			throw new IllegalArgumentException("@Api required");
		this.name = api.value();
		this.description = api.description();
		this.statuses = new ArrayList<>(api.statuses().length + 1);
		boolean has2xx = false;
		for (Status es : api.statuses()) {
			HttpStatus hs;
			try {
				hs = HttpStatus.valueOf(es.code());
			} catch (IllegalArgumentException e) {
				hs = null;
			}
			if (hs != null && hs.is2xxSuccessful())
				has2xx = true;
			StatusObject so = new StatusObject(es);
			if (StringUtils.isBlank(so.getMessage()) && hs != null)
				so.setMessage(hs.getReasonPhrase());
			this.statuses.add(so);
		}
		if (!has2xx) {
			ResponseStatus rs = AnnotatedElementUtils.findMergedAnnotation(apiDocMethod, ResponseStatus.class);
			if (rs != null)
				this.statuses.add(0, new StatusObject(rs.value()));
			else
				this.statuses.add(0, new StatusObject(HttpStatus.OK));
		}

		Authorize authorize = method.getAnnotation(Authorize.class);
		if (authorize == null)
			authorize = clazz.getAnnotation(Authorize.class);
		if (authorize != null) {
			String[] ifAnyGranted = authorize.ifAnyGranted();
			if (ifAnyGranted != null && ifAnyGranted.length > 0) {
				Set<String> set = new LinkedHashSet<>();
				for (String s : ifAnyGranted) {
					String[] arr = s.split("\\s*,\\s*");
					for (String ss : arr)
						set.add(ss);
				}
				requiredAuthorities = set.toArray(new String[0]);
			}
		}

		RequestMapping requestMapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
		if (requestMapping != null) {
			String murl = "";
			String curl = "";
			String[] values = requestMapping.value();
			if (values.length > 0)
				murl = values[0];
			RequestMapping requestMappingWithClass = AnnotatedElementUtils.findMergedAnnotation(clazz,
					RequestMapping.class);
			if (requestMappingWithClass != null) {
				values = requestMappingWithClass.value();
				if (values.length > 0)
					curl = values[0];
			}
			if (StringUtils.isNotBlank(murl) || StringUtils.isNotBlank(curl))
				url = curl + murl;
			RequestMethod[] rms = requestMapping.method();
			if (rms.length > 0) {
				methods = new String[rms.length];
				for (int i = 0; i < rms.length; i++)
					methods[i] = rms[i].name();
			}
			consumes = requestMapping.consumes();
			produces = requestMapping.produces();
		}

		Class<?> responseBodyClass = method.getReturnType();
		Type responseBodyGenericType = GenericTypeResolver.resolveType(method.getGenericReturnType(), clazz);
		boolean isRestResult = false;
		if (responseBodyClass == RestResult.class) {
			isRestResult = true;
			responseBodyGenericType = ((ParameterizedType) responseBodyGenericType).getActualTypeArguments()[0];
			if (responseBodyGenericType instanceof Class)
				responseBodyClass = (Class<?>) responseBodyGenericType;
			else if (responseBodyGenericType instanceof ParameterizedType)
				responseBodyClass = (Class<?>) ((ParameterizedType) responseBodyGenericType).getRawType();
		}
		if (Iterable.class.isAssignableFrom(responseBodyClass)) {
			responseBodyType = "collection";
		} else if (ResultPage.class.isAssignableFrom(responseBodyClass)) {
			responseBodyType = "resultPage";
		} else if (Flux.class.isAssignableFrom(responseBodyClass)) {
			responseBodyType = "collection";
		}
		if (responseBodyGenericType instanceof ParameterizedType) {
			ParameterizedType pt = (ParameterizedType) responseBodyGenericType;
			Type type = pt.getActualTypeArguments()[0];
			if (type instanceof Class) {
				responseBodyClass = (Class<?>) type;
			} else if (type instanceof ParameterizedType) {
				responseBodyGenericType = pt;
				pt = (ParameterizedType) type;
				if (Iterable.class.isAssignableFrom((Class<?>) pt.getRawType())) {
					responseBodyType = "collection";
					responseBodyClass = (Class<?>) pt.getActualTypeArguments()[0];
				}
			}
		}

		Fields responseFields = AnnotationUtils.findAnnotation(apiDocMethod, Fields.class);
		Class<?> view = null;
		Object responseSample = ApiDocHelper.generateSample(apiDocClazz, apiDocMethod, responseFields);
		if (responseSample == null)
			responseSample = ApiDocHelper.createSample(responseBodyGenericType);
		if (responseSample instanceof String && !isRestResult) {
			responseBodySample = (String) responseSample;
		} else if (responseSample != null) {
			if (responseSample instanceof Flux) {
				responseSample = Collections.singletonList(((Flux<?>) responseSample).blockFirst());
			} else if (responseSample instanceof Mono) {
				responseSample = ((Mono<?>) responseSample).block();
			} else if (responseSample instanceof DeferredResult)
				responseSample = ((DeferredResult<?>) responseSample).getResult();
			else if (responseSample instanceof Future)
				responseSample = ((Future<?>) responseSample).get();
			else if (responseSample instanceof Callable)
				responseSample = ((Callable<?>) responseSample).call();
			else if (responseSample instanceof ResponseEntity)
				responseSample = ((ResponseEntity<?>) responseSample).getBody();
			else if (responseSample instanceof MappingJacksonValue) {
				view = ((MappingJacksonValue) responseSample).getSerializationView();
				responseSample = ((MappingJacksonValue) responseSample).getValue();
			}

			if (isRestResult && !(responseSample instanceof RestResult))
				responseSample = RestResult.of(responseSample);

			if (responseSample instanceof Resource || responseSample instanceof InputStream
					|| responseSample instanceof byte[]) {
				download = true;
			} else {
				JsonView jsonView = AnnotationUtils.findAnnotation(method, JsonView.class);
				if (view == null && jsonView != null)
					view = jsonView.value()[0];
				if (view == null)
					responseBodySample = objectMapper.writeValueAsString(responseSample);
				else
					responseBodySample = objectMapper.writerWithView(view).writeValueAsString(responseSample);
			}
		}

		if (!download && !BeanUtils.isSimpleValueType(responseBodyClass))
			responseBody = FieldObject.createList(responseBodyClass, responseFields, view, false);

		Class<?>[] parameterTypes = method.getParameterTypes();
		Type[] genericParameterTypes = method.getGenericParameterTypes();
		if (parameterTypes.length > 0) {
			String[] parameterNames = ReflectionUtils.getParameterNames(method);
			Annotation[][] apiDocParameterAnnotations = apiDocMethod.getParameterAnnotations();
			Annotation[][] array = method.getParameterAnnotations();
			for (int i = 0; i < parameterTypes.length; i++) {
				String parameterName = parameterNames[i];
				Class<?> parameterType = parameterTypes[i];
				Type genericParameterType = genericParameterTypes[i];
				if (genericParameterType instanceof TypeVariable)
					genericParameterType = GenericTypeResolver.resolveType(genericParameterType, clazz);
				Annotation[] apiDocAnnotations = apiDocParameterAnnotations[i];
				Annotation[] annotations = array[i];
				Field fd = null;
				Fields requestFields = null;
				for (int j = 0; j < apiDocAnnotations.length; j++) {
					Annotation anno = apiDocAnnotations[j];
					if (anno instanceof Field) {
						fd = (Field) anno;
					}
				}
				for (int j = 0; j < apiDocAnnotations.length; j++) {
					Annotation anno = apiDocAnnotations[j];
					if (anno instanceof Fields) {
						requestFields = (Fields) anno;
					}
				}
				boolean bindAnnotationPresent = false;
				for (int j = 0; j < annotations.length; j++) {
					Annotation anno = annotations[j];
					if (ClassUtils.getPackageName(anno.annotationType())
							.equals(ClassUtils.getPackageName(RequestBody.class))
							|| anno.annotationType().equals(Qualifier.class)
							|| anno.annotationType().isAnnotationPresent(Qualifier.class)) {
						// @RequestBody @Qualifier @LoggedInUser ...
						bindAnnotationPresent = true;
					}

					if (anno instanceof RequestBody) {
						RequestBody ann = (RequestBody) anno;
						requestBodyRequired = ann.required();
						Class<?> requestBodyClass = parameterType;
						if (Iterable.class.isAssignableFrom(requestBodyClass)) {
							requestBodyType = "collection";
						} else if (Map.class.isAssignableFrom(requestBodyClass)) {
							requestBodyType = "map";
						} else if (ResultPage.class.isAssignableFrom(requestBodyClass)) {
							requestBodyType = "resultPage";
						}
						if (genericParameterType instanceof ParameterizedType) {
							ParameterizedType pt = (ParameterizedType) genericParameterType;
							Type[] actualTypeArguments = pt.getActualTypeArguments();
							if (pt.getRawType() == Optional.class) {
								requestBodyRequired = false;
								genericParameterType = actualTypeArguments[0];
								if (genericParameterType instanceof ParameterizedType) {
									pt = (ParameterizedType) genericParameterType;
									actualTypeArguments = pt.getActualTypeArguments();
								}
							}
							if (actualTypeArguments.length == 1) {
								Type type = actualTypeArguments[0];
								if (type instanceof Class) {
									Class<?> cls = (Class<?>) type;
									if (!BeanUtils.isSimpleValueType(cls))
										requestBodyClass = cls;
								}
							}
						}
						if (requestBodyClass.getGenericSuperclass() instanceof ParameterizedType) {
							ParameterizedType pt = (ParameterizedType) requestBodyClass.getGenericSuperclass();
							requestBodyClass = (Class<?>) pt.getActualTypeArguments()[0];
						}
						if (requestBodyClass == String.class) {
							String sample = (String) ApiDocHelper.createSample(String.class);
							if (consumes != null && consumes.length > 0
									&& MediaType.parseMediaType(consumes[0]).getType().equals("text"))
								requestBodySample = sample;
							else
								requestBodySample = objectMapper.writeValueAsString(sample);
						} else {
							requestBody = FieldObject.createList(requestBodyClass, requestFields, true);
							if (requestBody != null) {
								Object requestSample = ApiDocHelper.generateSample(apiDocClazz, null, requestFields);
								if (requestSample == null)
									requestSample = ApiDocHelper.createSample(genericParameterType);
								if (requestSample instanceof String) {
									requestBodySample = (String) requestSample;
								} else if (requestSample instanceof Map) {
									requestBodySample = objectMapper.writeValueAsString(requestSample);
								} else if (requestSample != null) {
									SimpleFilterProvider filters = new SimpleFilterProvider();
									filters.addFilter("desensitizer",
											SimpleBeanPropertyFilter.filterOutAllExcept(requestBody.stream()
													.map(FieldObject::getName).collect(Collectors.toSet())));
									ObjectWriter objectWriter = objectMapper.copy()
											.addMixIn(requestBodyClass, JsonFilterMixIn.class)
											.setFilterProvider(filters).writer();
									requestBodySample = objectWriter.writeValueAsString(requestSample);
								}
							}
						}
					} else if (anno instanceof PathVariable) {
						PathVariable ann = (PathVariable) anno;
						String nameInAnn = StringUtils.isNotBlank(ann.name()) ? ann.name() : ann.value();
						String fieldName = StringUtils.isNotBlank(nameInAnn) ? nameInAnn : parameterName;
						Class<?> fieldType = parameterType;
						boolean fieldRequired = ann.required();
						if (parameterType == Optional.class) {
							fieldType = (Class<?>) ((ParameterizedType) genericParameterType)
									.getActualTypeArguments()[0];
							fieldRequired = false;
						}
						if (!Map.class.isAssignableFrom(fieldType))
							pathVariables.add(FieldObject.create(fieldName, fieldType, fieldRequired, null, fd));
					} else if (anno instanceof RequestParam || anno instanceof RequestPart) {
						String fieldName;
						boolean fieldRequired;
						String defaultValue = null;
						if (anno instanceof RequestParam) {
							RequestParam ann = (RequestParam) anno;
							String nameInAnn = StringUtils.isNotBlank(ann.name()) ? ann.name() : ann.value();
							fieldName = StringUtils.isNotBlank(nameInAnn) ? nameInAnn : parameterName;
							fieldRequired = ann.required();
							defaultValue = ann.defaultValue();
						} else {
							RequestPart ann = (RequestPart) anno;
							String nameInAnn = StringUtils.isNotBlank(ann.name()) ? ann.name() : ann.value();
							fieldName = StringUtils.isNotBlank(nameInAnn) ? nameInAnn : parameterName;
							fieldRequired = ann.required();
						}
						Type fieldType = genericParameterType;
						if (parameterType == Optional.class) {
							fieldType = ((ParameterizedType) genericParameterType).getActualTypeArguments()[0];
							fieldRequired = false;
						}
						if (!Map.class.isAssignableFrom(parameterType)) {
							FieldObject fo = FieldObject.create(fieldName, fieldType, fieldRequired, defaultValue, fd);
							if (fo != null)
								requestParams.add(fo);
						}
					} else if (anno instanceof RequestHeader) {
						RequestHeader ann = (RequestHeader) anno;
						String nameInAnn = StringUtils.isNotBlank(ann.name()) ? ann.name() : ann.value();
						String fieldName = StringUtils.isNotBlank(nameInAnn) ? nameInAnn : parameterName;
						Type fieldType = genericParameterType;
						boolean fieldRequired = ann.required();
						if (parameterType == Optional.class) {
							fieldType = ((ParameterizedType) genericParameterType).getActualTypeArguments()[0];
							fieldRequired = false;
						}
						if (!Map.class.isAssignableFrom(parameterType)) {
							FieldObject fo = FieldObject.create(fieldName, fieldType, fieldRequired, ann.defaultValue(),
									fd);
							if (fo != null)
								requestHeaders.add(FieldObject.create(fieldName, fieldType, fieldRequired,
										ann.defaultValue(), fd));
						}
					} else if (anno instanceof CookieValue) {
						CookieValue ann = (CookieValue) anno;
						String nameInAnn = StringUtils.isNotBlank(ann.name()) ? ann.name() : ann.value();
						String fieldName = StringUtils.isNotBlank(nameInAnn) ? nameInAnn : parameterName;
						Type fieldType = genericParameterType;
						boolean fieldRequired = ann.required();
						if (parameterType == Optional.class) {
							fieldType = ((ParameterizedType) genericParameterType).getActualTypeArguments()[0];
							fieldRequired = false;
						}
						if (!Map.class.isAssignableFrom(parameterType)) {
							FieldObject fo = FieldObject.create(fieldName, fieldType, fieldRequired, ann.defaultValue(),
									fd);
							if (fo != null)
								cookieValues.add(FieldObject.create(fieldName, fieldType, fieldRequired,
										ann.defaultValue(), fd));
						}
					}
				}
				if (!bindAnnotationPresent && Arrays.asList(methods).contains("GET")) {
					// bind object not @RequestParam
					if (!parameterType.isPrimitive()) { // int.class
						String paramPackageName = ClassUtils.getPackageName(parameterType);
						if (!paramPackageName.startsWith("java.") && !paramPackageName.startsWith("javax.")
								&& !paramPackageName.startsWith("org.springframework.")
								&& Serializable.class.isAssignableFrom(parameterType)) {
							requestParams.addAll(FieldObject.createList(parameterType, requestFields, true));
						}
					}
				}
			}

		}
	}

	@JsonFilter("desensitizer")
	static class JsonFilterMixIn {

	}

}
