package org.ironrhino.security.model;

import javax.persistence.Entity;
import javax.persistence.Table;

import org.ironrhino.common.record.RecordAware;
import org.ironrhino.core.aop.PublishAware;
import org.ironrhino.core.metadata.AutoConfig;
import org.ironrhino.core.metadata.Richtable;
import org.ironrhino.core.search.elasticsearch.annotations.Searchable;
import org.ironrhino.core.spring.configuration.ClassPresentConditional;

@RecordAware
@PublishAware
@AutoConfig
@Searchable
@Entity
@Table(name = "user")
@Richtable(order = "username asc", celleditable = false, actionColumnButtons = "<@btn view='input' label='edit'/> <@btn action='resetPassword' confirm=true/>")
@ClassPresentConditional("org.ironrhino.security.service.UserManagerImpl")
public class User extends BaseUser {

	private static final long serialVersionUID = 7307419528067871480L;

}
