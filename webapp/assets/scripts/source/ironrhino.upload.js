Initialization.upload = function() {
	$(document).on('click', '#files button.reload', function() {
				ajax({
							type : $('#upload_form').attr('method'),
							url : $('#upload_form').formAction(),
							data : $('#upload_form').serialize(),
							replacement : 'files'
						});
			}).on('click', '#files button.mkdir', function() {
		$.alerts({
			type : 'prompt',
			value : 'newfolder',
			callback : function(t) {
				if (t) {
					var folder = $('#current_folder').text() + t;
					var url = $('#upload_form').formAction();
					if (!url)
						url = CONTEXT_PATH + '/common/upload';
					url += '/mkdir' + encodeURI(folder);
					ajax({
								url : url,
								dataType : 'json',
								success : function() {
									$('#folder').val(folder);
									$('#files button.reload').click();
									if (typeof history.pushState != 'undefined') {
										var url = $('#upload_form')
												.formAction();
										if (!url)
											url = CONTEXT_PATH
													+ '/common/upload';
										url += '/list' + encodeURI(folder)
										history.pushState(url, '', url);
									}
								}
							});
				}
			}
		});
	}).on('click', '#files button.snapshot', function() {
		$.snapshot({
					onsnapshot : function(canvas, timestamp) {
						var filename = 'snapshot_'
								+ $.format.date(new Date(timestamp),
										'yyyyMMddHHmmssSSS') + '.png';
						var file;
						if (canvas && canvas.toBlob)
							canvas.toBlob(function(blob) {
										uploadFiles([blob], [filename]);
									}, 'image/png');
					},
					onerror : function(msg) {
						Message.showError(msg);
					}
				});
	}).on('click', '#files button.delete', function() {
				if (!$('#files tbody input:checked').length)
					Message.showMessage('no.selection');
				else
					deleteFiles()
			}).on('keyup compositionstart compositionend',
			'#files input.filter', function(event) {
				if (event.type == 'compositionstart') {
					$(this).data('ime', true);
					return false;
				}
				if (event.type == 'compositionend') {
					$(this).removeData('ime');
					return false;
				} else if ($(this).data('ime'))
					return false;
				var tbody = $('tbody', $(event.target).closest('table'));
				var keyword = this.value.toLowerCase();
				if (event.keyCode == 8) {
					if (!keyword)
						$('tr:hidden', tbody).show();
					else
						$('tr:hidden', tbody).each(function(i, v) {
							var tr = $(v);
							var filename = $('td:eq(1)', tr).text()
									.toLowerCase();
							if (filename.indexOf(keyword) >= 0)
								tr.show();
						});
				} else {
					$('tr:visible', tbody).each(function(i, v) {
								var tr = $(v);
								var filename = $('td:eq(1)', tr).text()
										.toLowerCase();
								if (filename.indexOf(keyword) < 0)
									tr.hide();
								else
									tr.show();
							});
				}
			});
}
Observation.upload = function(container) {
	var c = $(container);
	var selector = '#upload_form';
	var upload_form = c.is(selector) ? c : $(selector, c);
	if (upload_form.length && typeof window.FileReader != 'undefined') {
		upload_form.on('dragover', function(e) {
					$(this).addClass('drophover');
					return false;
				}).on('dragleave', function(e) {
					$(this).removeClass('drophover');
					return false;
				}).on('drop', function(e) {
					e.preventDefault();
					$(this).removeClass('drophover');
					uploadFiles(e.originalEvent.dataTransfer.files);
					return true;
				});
		$(document).on('dragover', function(e) {
					return false;
				}).on('drop', function(e) {
			var id = e.originalEvent.dataTransfer.getData('Text');
			var target = $(e.target);
			if (!id || target.is('#upload_form')
					|| target.parents('#upload_form').length)
				return true;
			var i = id.lastIndexOf('/');
			if (i > 0)
				id = id.substring(i + 1);
			if (e.preventDefault)
				e.preventDefault();
			if (e.stopPropagation)
				e.stopPropagation();
			deleteFiles(id);
		});
	}

	$('.uploaditem', container).prop('draggable', true).each(function() {
		var t = $(this);
		t.on('dragstart', function(e) {
					e.originalEvent.dataTransfer.effectAllowed = 'copy';
					e.originalEvent.dataTransfer.setData('Text', $(
									':input:eq(0)', t.closest('tr'))
									.attr('value'));
				});
	});

	$('.filename').dblclick(function() {
		if (this.contentEditable !== true) {
			$(this).removeAttr('draggable').data('oldvalue', $(this).text())
					.css('cursor', 'text');
			this.contentEditable = true;
			$(this).focus();
		}
	}).blur(function() {
		var oldvalue = $(this).data('oldvalue');
		var newvalue = $(this).text();
		if (oldvalue != newvalue) {
			var url = $('#upload_form').formAction();
			if (!url)
				url = CONTEXT_PATH + '/common/upload';
			url += '/rename/' + encodeURI(oldvalue);
			$.ajax({
				url : url,
				data : {
					folder : $('#upload_form [name="folder"]').val(),
					filename : newvalue
				},
				beforeSend : Indicator.show,
				success : function(data) {
					Indicator.hide();
					if (typeof data == 'string') {
						var html = data
								.replace(/<script(.|\s)*?\/script>/g, '');
						var div = $('<div/>').html(html);
						var message = $('#message', div);
						if (message.html()) {
							if ($('.action-error', message).length
									|| !$('#upload_form input[name="pick"]').length)
								if ($('#message').length)
									$('#message').html(message.html());
								else
									$('<div id="message">' + message.html()
											+ '</div>')
											.prependTo($('#content'));
						}
					} else {
						Message
								.showActionSuccessMessage(data.actionSuccessMessage);
						Message.showActionMessage(data.actionMessages);
						Message.showActionWarning(data.actionWarning);
						Message.showActionError(data.actionErrors);
					}
					$('#files button.reload').trigger('click');
				}
			});
		}
	});

};

function deleteFiles(file) {
	var func = function() {
		var url = $('#upload_form').formAction();
		if (!url)
			url = CONTEXT_PATH + '/common/upload';
		url += '/delete';
		var options = {
			type : $('#upload_form').attr('method'),
			url : url,
			dataType : 'json',
			complete : function() {
				$('#files button.reload').click();
			}
		};
		if (file) {
			var data = $('#upload_form').serialize();
			var params = [];
			params.push('id=' + file);
			if (data) {
				var arr = data.split('&');
				for (var i = 0; i < arr.length; i++) {
					var arr2 = arr[i].split('=', 2);
					if (arr2[0] != 'id')
						params.push(arr[i]);
				}
			}
			options.data = params.join('&');
		} else {
			options.data = $('#upload_form').serialize();
		}
		ajax(options);
	};
	if (VERBOSE_MODE != 'LOW') {
		$.alerts({
					type : 'confirm',
					message : MessageBundle.get('confirm.delete'),
					callback : function(b) {
						if (b) {
							func();
						}
					}
				});
	} else {
		func();
	}
}
function uploadFiles(files, filenames) {
	if (files && files.length) {
		var data = {};
		if (filenames && filenames.length)
			data.filename = filenames;
		var f = $('#upload_form');
		$.each($.formToArray(f[0]), function() {
					data[this.name] = this.value;
				});
		return $.ajaxupload(files, ajaxOptions({
							url : f.formAction(),
							name : f.find('input[type="file"]').attr('name'),
							data : data,
							replacement : 'files'
						}));
	}
}
