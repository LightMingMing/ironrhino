package org.ironrhino.core.spring.configuration;

import java.io.IOException;
import java.lang.annotation.Annotation;

import org.ironrhino.core.util.AnnotationUtils;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.AnnotationTypeFilter;

public class CustomAnnotationTypeFilter extends AnnotationTypeFilter {

	public CustomAnnotationTypeFilter(Class<? extends Annotation> annotationType) {
		super(annotationType);
	}

	public CustomAnnotationTypeFilter(Class<? extends Annotation> annotationType, boolean considerMetaAnnotations) {
		super(annotationType, considerMetaAnnotations, false);
	}

	public CustomAnnotationTypeFilter(Class<? extends Annotation> annotationType, boolean considerMetaAnnotations,
			boolean considerInterfaces) {
		super(annotationType, considerMetaAnnotations, considerInterfaces);
	}

	@Override
	public boolean match(MetadataReader mr, MetadataReaderFactory mrf) throws IOException {
		if (!super.match(mr, mrf))
			return false;
		AnnotationMetadata metadata = mr.getAnnotationMetadata();
		RunLevelConditional rc = AnnotationUtils.getAnnotation(metadata, RunLevelConditional.class);
		if (rc != null && !RunLevelCondition.matches(rc.value(), rc.negated()))
			return false;
		StageConditional sc = AnnotationUtils.getAnnotation(metadata, StageConditional.class);
		if (sc != null && !StageCondition.matches(sc.value(), sc.negated()))
			return false;
		ClassPresentConditional cpc = AnnotationUtils.getAnnotation(metadata, ClassPresentConditional.class);
		if (cpc != null && !ClassPresentCondition.matches(cpc.value(), cpc.negated()))
			return false;
		ResourcePresentConditional rpc = AnnotationUtils.getAnnotation(metadata, ResourcePresentConditional.class);
		if (rpc != null && !ResourcePresentCondition.matches(rpc.value(), rpc.negated()))
			return false;
		ApplicationContextPropertiesConditional acpc = AnnotationUtils.getAnnotation(metadata,
				ApplicationContextPropertiesConditional.class);
		if (acpc != null && !ApplicationContextPropertiesCondition.matches(acpc.key(), acpc.value(), acpc.negated()))
			return false;
		AddressAvailabilityConditional aac = AnnotationUtils.getAnnotation(metadata,
				AddressAvailabilityConditional.class);
		if (aac != null && !AddressAvailabilityCondition.matches(aac.address(), aac.timeout(), aac.negated()))
			return false;
		return true;
	}

}
