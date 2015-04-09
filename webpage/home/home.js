
function switchToLoggedOutState(){
	hideError();
	$("#hello").hide();
	$("#forLocalsOnly").hide();
	$("#goodbye").show();
}


function switchToLoggedInState(){
	$("#goodbye").hide();
	$("#hello").show();
}


function showFunctionalityForLocalUsers(){
	$("#forLocalsOnly").show();
}


function displayUserInfo(uinfo){

	switchToLoggedInState();

	$("#givenName").html(uinfo.givenName);
	$("#surname").html(uinfo.surname);
	$("#mail").html(uinfo.mail);
}


function reportError(xhr){
	$("#errorMessage div").html(xhr.responseText);
	$("#errorMessage").show();
}


function hideError(){
	$("#errorMessage").hide();
}


function enterKeyHandler(innerFun){
	return function(e){
		if(e.which == 13) innerFun(e);
	}
}


function signOut(){
	$.get("/logout")
		.done(switchToLoggedOutState)
		.fail(reportError);
}


function changePassword(){
	var query = $.param({
		oldPass: $("#oldPassword").val(),
		newPass: $("#newPassword").val()
	});

	$.post("/password/changepassword", query)
		.fail(reportError)
		.done(function(){
			$("#oldPassword").val('');
			$("#newPassword").val('');
		});
}


$(function(){
	$.getJSON('/whoami')
		.done(displayUserInfo)
		.fail(switchToLoggedOutState);

	$.getJSON('/password/amilocal')
		.done(function(userIsLocal){
			if(userIsLocal) showFunctionalityForLocalUsers();
		});

	$("#signOutButton").click(signOut);
	$("#changePasswordButton").click(changePassword);
	$("#newPassword").keypress(enterKeyHandler(changePassword));
	$("#oldPassword").keypress(enterKeyHandler(function(){$("#newPassword").focus()}));
});

