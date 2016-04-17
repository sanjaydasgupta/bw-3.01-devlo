app.controller("BuildWhizNgAppDashboardCtrl", function ($scope, BuildWhizNgAppDashboardService) {
    // alert("dashboard Load");
   // alert(gID);
     
    $scope.manageProject_Click = function () {
        location.hash = "#!/projects";
    }

    $scope.manageMongodb_Click = function () {
        location.hash = "#!/mongodb";
    }

    $scope.manageNextGen_Click = function () {
        location.hash = "#!/nextGen";
    }

    $scope.userProfile_Click = function () {
        location.hash = "#!/userProfile";
    }

});