function setBrowserTitle() {

    if (document.getElementById("hndLoggedInFName").value != "") {

        document.getElementById('ttlBuildWhizMain').innerHTML = "BW " + document.getElementById("hndLoggedInFName").value + " " + document.getElementById("hndLoggedInLName").value;
    }
}

var baseURL = "";

var gProjectManager = false;


var serviceErrorMessege = "An unidentified error has occured. Please contact support";

//======================show hide divWelcomeMsg

//function btnLogOut() {
   
//    $("#divWelcomeMsg").hide();
//}


//create new project Modal centering
$("#modalCreateNewProject").modal('show').css({
    'margin-top': function () { //vertical centering
        return -($(this).height() / 2);
    },
    'margin-left': function () { //Horizontal centering
        return -($(this).width() / 2);
    }
});

//body on load index.html 
function logIn_onLoad() {

   
    document.getElementById("hndLoggedInID").value = "";
    document.getElementById("hndLoggedInFName").value = "";
    document.getElementById("hndLoggedInLName").value = "";

    document.getElementById("hndPersonID").value = "";
    document.getElementById("hndPersonFName").value = "";
    document.getElementById("hndPersonLName").value = "";

    document.getElementById("hdnLoggedInProjectManager").value = "n";

    gProjectManager = false;

}



////dynamic css
//$(document).ready(function () {
//    $("a.col-lg-4.col-md-6.col-sm-4.activityLinkHref").css({ "background-color": "yellow", "font-size": "200%" });
//    alert($(".activityPanel .administratorCtrlPartHr").height());
//});












