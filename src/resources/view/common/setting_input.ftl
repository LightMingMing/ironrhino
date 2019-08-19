<#ftl output_format='HTML'>
<#assign view=Parameters.view!/>
<!DOCTYPE html>
<html>
<head>
<title><#if setting.new>${getText('create')}<#else>${getText('edit')}</#if>${getText('setting')}</title>
</head>
<body>
<@s.form id="setting_input" action="${actionBaseUrl}/save" method="post" class="ajax form-horizontal${view?has_content?then('',' importable')}">
	<#if !setting.new>
		<@s.hidden name="setting.id"/>
	</#if>
	<@s.hidden name="setting.version" class="version"/>
	<#if view=='embedded'>
		<@s.hidden name="setting.key"/>
		<@s.hidden name="setting.description"/>
	<#elseif view=='brief'>
		<@s.hidden name="setting.key"/>
		<@s.textarea name="setting.description" tabindex="-1" readonly=true class="input-xxlarge"/>
	<#else>
		<@s.textfield name="setting.key" class="required checkavailable input-xxlarge"/>
	</#if>
	<#if view=='embedded'>
	<@s.textarea theme="simple" name="setting.value" style="width:95%;" class="${Parameters.class!Parameters.cssClass!}" maxlength="4000"/>
	<#else>
	<@s.textarea name="setting.value" class="input-xxlarge" maxlength="4000"/>
	</#if>
	<#if !(view=='embedded'||view=='brief')>
		<@s.textarea name="setting.description" class="input-xxlarge" maxlength="4000"/>
	</#if>
	<@s.submit label=getText('save') class="btn-primary"/>
</@s.form>
</body>
</html>
