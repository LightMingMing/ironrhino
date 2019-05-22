package org.ironrhino.core.metadata;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.hibernate.criterion.MatchMode;

@Target({ METHOD, FIELD })
@Retention(RUNTIME)
public @interface UiConfig {

	String DEFAULT_TYPE = "input";

	String DEFAULT_INPUT_TYPE = "text";

	String alias() default "";

	String description() default "";

	String id() default "";

	String type() default DEFAULT_TYPE; // input,textarea,enum,select,multiselect,checkbox,listpick,dictionary,schema...

	String inputType() default DEFAULT_INPUT_TYPE; // text,password,email,number...

	int maxlength() default 0;

	String regex() default "";

	boolean trim() default true;

	String cssClass() default "";

	String thCssClass() default "";

	int displayOrder() default Integer.MAX_VALUE;

	boolean required() default false;

	boolean unique() default false;

	Readonly readonly() default @Readonly;

	boolean hidden() default false;

	Hidden hiddenInList() default @Hidden;

	Hidden hiddenInInput() default @Hidden;

	Hidden hiddenInView() default @Hidden;

	boolean shownInPick() default false;

	boolean searchable() default false;

	String template() default "";

	String listTemplate() default "";

	String viewTemplate() default "";

	String inputTemplate() default "";

	String csvTemplate() default "";

	String width() default "";

	String dynamicAttributes() default ""; // json map

	String cellDynamicAttributes() default ""; // json map

	String listKey() default "";

	String listValue() default "";

	String listOptions() default ""; // for select,multiselect

	String cellEdit() default "";

	String pickUrl() default "";// for listpick treeselect

	String templateName() default ""; // for dictionary,schema

	boolean excludedFromLike() default false;

	boolean excludedFromCriteria() default false;

	boolean excludedFromOrdering() default false;

	boolean excludedFromQuery() default false;

	String group() default "";

	boolean suppressViewLink() default false;

	boolean embeddedAsSingle() default false;

	boolean showSum() default false;

	MatchMode queryMatchMode() default MatchMode.ANYWHERE;

	boolean queryWithRange() default false;

	boolean queryWithMultiplePick() default false;

}