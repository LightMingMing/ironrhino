<#ftl output_format='HTML'>
<a class="brand" href="<@url value="/"/>"><#assign defaultBrand=getText(statics['org.ironrhino.core.util.AppInfo'].getAppName())?cap_first/><#if printSetting??><@printSetting key="brand" default=defaultBrand/><#else>${defaultBrand}</#if></a>
