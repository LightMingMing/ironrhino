<#ftl output_format='HTML'>
<#macro listFields fields>
<table class="table">
<thead>
	<tr>
		<th style="width:250px;">名字</th>
		<th style="width:100px;">类型</th>
		<th style="width:60px;">必填</th>
		<th style="width:80px;">默认值</th>
		<th>备注</th>
	</tr>
</thead>
<tbody>
	<#list fields as field>
	<tr>
		<td>${field.name} 
		<#assign label=field.label!/>
		<#if !label?has_content>
		<#assign label=''/>
		<#list field.name?split('.') as var>
		<#assign label+=getText(var)/>
		</#list>
		</#if>
		<#if label!=field.name> <span class="label pull-right">${label}</span></#if>
		</td>
		<td>${field.type!}<#if field.multiple> <span class="label pull-right">多值</span></#if></td>
		<td>${getText(field.required?string)}</td>
		<td>${field.defaultValue!}</td>
		<td><#if field.description?has_content>${getText(field.description)}</#if>
			<#if field.values?has_content>
			枚举值:
			<ul class="unstyled">
				<#list field.values as key,value>
				<li>${key}<#if value!=key> <span class="label">${value}</span></#if></li>
				</#list>
			</ul>
			</#if>
		</td>
	</tr>
	</#list>
</tbody>
</table>
</#macro>
<!DOCTYPE html>
<html>
<head>
<title>${getText('docs')}<#if version?has_content> ${version}</#if></title>
</head>
<body>

  <div class="row<#if fluidLayout>-fluid</#if>">
    <div class="span3">
		<div class="accordion" id="api-accordion">
		<@classPresentConditional value="org.ironrhino.security.oauth.server.model.Authorization">
		  <div class="accordion-group">
		    <div class="accordion-heading">
		      <a class="accordion-toggle" data-toggle="collapse" data-parent="#api-accordion" href="#overview">
		     	接入基础
		      </a>
		    </div>
		    <div id="overview" class="accordion-body collapse<#if !module?has_content> in</#if>">
		      <div class="accordion-inner">
		      	<#assign partial=Parameters.partial!/>
		        <ul class="nav nav-list">
					<li<#if partial=='prerequisite'> class="active"</#if>><a href="${actionBaseUrl}?partial=prerequisite<#if version??>&version=${version}</#if>" class="ajax view history" data-replacement="apidoc">接入准备</a></li>
					<li<#if partial=='oauth2'> class="active"</#if>><a href="${actionBaseUrl}?partial=oauth2<#if version??>&version=${version}</#if>" class="ajax view history" data-replacement="apidoc">OAuth2</a></li>
					<li<#if partial=='status'> class="active"</#if>><a href="${actionBaseUrl}?partial=status<#if version??>&version=${version}</#if>" class="ajax view history" data-replacement="apidoc">通用返回状态消息</a></li>
				</ul>
		      </div>
		    </div>
		  </div>
		</@classPresentConditional>
		<#list apiModules as key,value>
		  <#assign _category=key>
		  <#if _category?has_content>
		  	<div class="accordion-group">
		    <div class="accordion-heading">
		      <a class="accordion-toggle" data-toggle="collapse" data-parent="#api-accordion" href="#category_${key?index}">${_category}</a>
		    </div>
		    <div id="category_${key?index}" class="accordion-body collapse<#if _category==category> in</#if>">
		    <div class="accordion-inner">
		    <div id="category_accordion_${key?index}" class="accordion">
		  </#if>
		  <#list value as apiModule>
		  <div class="accordion-group">
		    <div class="accordion-heading">
		      <a class="accordion-toggle" data-toggle="collapse" data-parent="#<#if _category?has_content>category_accordion_${key?index}<#else>api-accordion</#if>" href="#module_${key?index}_${apiModule?index}"<#if apiModule.description?has_content> title="${apiModule.description}"</#if>>${apiModule.name}</a>
		    </div>
		    <#assign currentModule = (!category?has_content||_category==category)&&module?has_content && module==apiModule.name>
		    <div id="module_${key?index}_${apiModule?index}" class="accordion-body collapse<#if currentModule> in</#if>">
		      <div class="accordion-inner">
		        <ul class="nav nav-list">
					<#list apiModule.apiDocs as apiDoc>
					<li<#if currentModule && api?has_content && api==apiDoc.name> class="active"</#if>><a href="${actionBaseUrl}?<#if _category?has_content>category=${_category?url}&</#if>module=${apiModule.name?url}&api=${apiDoc.name?url}<#if version??>&version=${version}</#if>" class="ajax view history" data-replacement="apidoc">${apiDoc.name}</a></li>
					</#list>
				</ul>
		      </div>
		    </div>
		  </div>
		  </#list>
		  <#if _category?has_content>
		  </div>
		  </div>
		  </div>
		  </div>
		  </#if>
		</#list>
		</div>
    </div>
    <div id="apidoc" class="span9">
		<#if apiDoc??>
			<h4 class="center">${apiDoc.name}</h4>
			<#if apiDoc.description?has_content>
			<div class="alert alert-info">
			  ${apiDoc.description?no_esc}
			</div>
			</#if>
			<table class="table">
				<tbody>
					<#if apiDoc.requiredAuthorities?has_content>
					<tr><td style="width:100px;">所需授权</td><td><#list apiDoc.requiredAuthorities as auth><span class="label label-info">${getText(auth)}</span> </#list></td></tr>
					</#if>
					<tr><td>请求方法</td><td><#list apiDoc.methods as method><span class="label label-info">${method}</span><#sep> </#list></td></tr>
					<tr><td>请求URL</td><td>${apiBaseUrl}${apiDoc.url}</td></tr>
					<#if apiDoc.consumes?has_content><tr><td>请求类型</td><td><#list apiDoc.consumes as type><span class="label label-info">${type}</span><#sep> </#list></td></tr></#if>
					<#if apiDoc.produces?has_content><tr><td>响应类型</td><td><#list apiDoc.consumes as type><span class="label label-info">${type}</span><#sep> </#list></td></tr></#if>
					<#if apiDoc.pathVariables?has_content><tr><td>URI变量</td><td class="compact-horizontal"><@listFields fields=apiDoc.pathVariables/></td></tr></#if>
					<#if apiDoc.requestParams?has_content><tr><td>请求参数</td><td class="compact-horizontal"><@listFields fields=apiDoc.requestParams/></td></tr></#if>
					<#if apiDoc.requestHeaders?has_content><tr><td>请求头</td><td class="compact-horizontal"><@listFields fields=apiDoc.requestHeaders/></td></tr></#if>
					<#if apiDoc.cookieValues?has_content><tr><td>请求Cookie</td><td class="compact-horizontal"><@listFields fields=apiDoc.cookieValues/></td></tr></#if>
					<#if apiDoc.requestBody?has_content><tr><td>请求消息体<#if !apiDoc.requestBodyRequired><br/><span class="label label-info">可选</span></#if><#if apiDoc.requestBodyType?has_content><br/><span class="label label-warning">${getText(apiDoc.requestBodyType)}</span></#if></td><td class="compact-horizontal"><@listFields fields=apiDoc.requestBody/></td></tr></#if>
					<#if apiDoc.requestBodySample?has_content><tr><td>请求消息体示例</td><td><code class="block json">${apiDoc.requestBodySample?no_esc}</code></td></tr></#if>
					<tr><td>响应状态</td><td class="compact-horizontal">
						<table class="table">
							<thead>
								<tr><th style="width:80px;">状态码</th><th style="width:200px;">消息</th><th>描述</th></tr>
							</thead>
							<tbody>
							<#list apiDoc.statuses as st>
							<tr><td>${st.code}</td><td>${st.message!}</td><td><#if st.description?has_content>${getText(st.description)}<#elseif st.code==200>正常处理请求返回此状态码, 这种情况下才有下面的响应消息体</#if></td></tr>
							</#list>
							</tbody>
						</table>
					</td></tr>
					<#if apiDoc.responseBody?has_content><tr><td>响应消息体<#if apiDoc.responseBodyType?has_content><br/><span class="label label-warning">${getText(apiDoc.responseBodyType)}</span></#if></td><td class="compact-horizontal"><@listFields fields=apiDoc.responseBody/></td></tr></#if>
					<#if apiDoc.responseBodySample?has_content><tr><td>响应消息体示例</td><td><code class="block json">${apiDoc.responseBodySample?no_esc}</code></td></tr></#if>
					<tr><td><h4>试玩一下</h4></td><td class="compact-horizontal"><#include "playground.ftl"/></td></tr>
				</tbody>
			</table>
		<#elseif partial?has_content>
			<#include "${partial}.ftl"/>
		</#if>
    </div>
  </div>
</body>
</html>
