<#ftl output_format='HTML'>
<!DOCTYPE html>
<html>
<head>
<title>${getText('console')}</title>
<script>
$(function(){
		$('#trigger .btn').click(function(){
			var t = $(this);
			$.ajax({
				type:'POST',
				url:'${actionBaseUrl}/executeJson',
				data:{
					expression : $(this).data('expression')||$(this).text(),
					scope: $(this).data('scope')
				},
				beforeSend:function(){
					t.prop('disabled',true);
				},
				success:function(data){
					if(data && data.actionErrors){
						Message.showActionError(data.actionErrors);
					}else{
						Message.showActionMessage(MessageBundle.get('success'));
					}
					t.prop('disabled',false);
				},
				error:function(data){
					Message.showActionError(MessageBundle.get('error'));
					t.prop('disabled',false);
				}
			});
		});
		<#if printSetting??>
		$('#switch input:checkbox').change(function(e){
			var t = this;
			var key = t.name;
			var value = t.checked;
			$.post('${actionBaseUrl}/executeJson',
								{
								expression : 'settingControl.setValue("'+key+'","'+value+'")'
								}
								,function(data){
									if(data && data.actionErrors){
										$(t).closest('.switch').bootstrapSwitch('toggleState');
										Message.showActionError(data.actionErrors);
										return;
									}
								});
		});
		</#if>
							
});
</script>
</head>
<body>
<@s.form id="form" action=actionBaseUrl method="post" class="form-horizontal ajax focus">
	<div class="row-fluid">
	<div class="span8">
	<@s.textarea name="expression" class="required" style="width:100%;height:100px;"/>
	</div>
	<div class="span4">
	<@s.select name="scope" class="input-medium" list="@org.ironrhino.core.metadata.Scope@values()" listKey="name" listValue="displayName"/>
	</div>
	</div>
	<@s.submit label=getText('submit') class="btn-primary"/>
</@s.form>
<hr/>

<#assign triggers = beans['applicationContextConsole'].getTriggers()>
<#if triggers?size gt 0>
<div id="trigger">
	<ul class="thumbnails">
	<#list triggers as key,value>
	<li class="span4">
	<button type="button" class="btn btn-block" data-scope="${value.name()}" data-expression="${key}">${getText(key)}</button>
	</li>
	</#list>
	</ul>
</div>
<hr/>
</#if>

<#if printSetting??>
<#assign settings = beans['settingControl'].getAllBooleanSettings()>
<#if settings?size gt 0>
<div id="switch">
	<style>
	li>div{
		line-height: 30px;
	}
	div.key{
		text-align: right;
		font-weight: bold;
	}
	</style>
	<ul class="thumbnails">
	<#list settings as setting>
	<li class="span4">
	<div class="row-fluid">
	<div class="span6 key"<#if setting.description?has_content> title="${setting.description}"</#if>>${getText(setting.key)}</div>
	<div class="span6"><input type="checkbox" name="${setting.key}"<#if setting.value=='true'> checked="checked"</#if> class="switch switch-round"/></div>
	</div>
	</li>
	</#list>
	</ul>
</div>
<hr/>
</#if>
</#if>
</body>
</html>