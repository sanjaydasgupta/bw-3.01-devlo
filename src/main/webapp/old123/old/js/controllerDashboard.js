﻿app.controller("BuildWhizNgAppDashboardCtrl", function ($scope, BuildWhizNgAppDashboardService) {
    // alert("dashboard Load");
   // alert(gID);

   $scope.isAdministrator = function () {
       var isAdmin = document.getElementById('hndLoggedInAdministrator').value == 'y';
       return isAdmin;
   }
     
    $scope.manageProject_Click = function () {
        location.hash = "#!/projects";
    }

    $scope.manageMongodb_Click = function () {
        location.hash = "#!/mongodb";
    }

    $scope.manageUserRoles_Click = function () {
        location.hash = "#!/userRoles";
    }

    $scope.manageBpmnViewer_Click = function () {
        location.hash = "#!/bpmnViewer";
    }

    $scope.userProfile_Click = function () {
        location.hash = "#!/userProfile";
    }

    $scope.manageDocuments_Click = function () {
        location.hash = "#!/documents";
    }

    $scope.rfi_Click = function () {
        location.hash = "#!/rfi";
    }

    $scope.manageAmazonS3_Click = function () {
        location.hash = "#!/amazonS3";
    }

});