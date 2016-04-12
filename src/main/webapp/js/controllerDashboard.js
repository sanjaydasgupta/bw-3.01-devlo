app.controller("BuildWhizNgAppDashboardCtrl", function ($scope, BuildWhizNgAppDashboardService) {
    // alert("dashboard Load");
   // alert(gID);
     
    $scope.manageProject_Click = function () {

        location.hash = "#!/projects";


    }



});