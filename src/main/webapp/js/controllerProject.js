app.controller("BuildWhizNgAppProjectCtrl", function ($http, $scope, BuildWhizNgAppProjectService) {   

    $("#divProjectCreateNew").hide();

    if (document.getElementById("hdnLoggedInProjectManager").value == "y") {
        $("#divProjectCreateNew").show();
    }
    $("#divPreLoad").hide();



    GetPhaseNames();
    GetPersons();



    //==============================================================Get All Phase Names 
   
    function GetPhaseNames() {

        var PhaseNamesData = BuildWhizNgAppProjectService.getPhaseNamesData();
        PhaseNamesData.then(function (response) {
            $scope.PhaseNamesUpdate = response.data;
            // alert(JSON.stringify(response.data));
        }, function (responseError) {
            alert(serviceErrorMessege);
        });
    };
   
    //==============================================================Get all person ()
    function GetPersons() {

        var personsData = BuildWhizNgAppProjectService.getPersonsData();
        personsData.then(function (response) {
            $scope.personsDataUpdate = response.data;
            // alert(JSON.stringify(response.data));
        }, function (responseError) {
            alert(serviceErrorMessege);
        });
    };


    GetAllProjectDetails();


    //==============================================================getAll projectPhase()
    var OnProjectRefreshHandle = $scope.$on('OnProjectRefresh', function (event, args) {

        GetAllProjectDetails();

    });

    //==============================================================refreshProject on refresh button click 
    $scope.btnRefreshProject = function () {        
        GetAllProjectDetails();

    }
    //==============================================================Get All Project Details 
    function GetAllProjectDetails() {

        var sPersonID = document.getElementById("hndPersonID").value;
        var projectData = BuildWhizNgAppProjectService.getProjectData(sPersonID);

        $("#divPreLoad").show();

        projectData.then(function (response) {
            $scope.projectDataUpdate = response.data;
            //alert(JSON.stringify(response.data));
           
            $("#divPreLoad").hide();

            if ($scope.projectDataUpdate.length <= 0) {
                //document.getElementById("lblMsg")
                $(".msgToggle").show;
                document.getElementById("lblMsg").innerHTML = "No projects found.";
               //alert("No projects found.");   
            }
            
        }, function (responseError) {
            $("#divPreLoad").hide();
            alert(serviceErrorMessege);
            
        });
    }


    //=================================================================set togglePublicProjectSync()
    $scope.togglePublicProjectSync = function (sProjectID, bChecked) {       

        $("#divPreLoad").show();

        var projectSetPublicData = BuildWhizNgAppProjectService.postProjectSetPublicData(sProjectID, bChecked);

        projectSetPublicData.then(function (response) {
            
            $scope.setProjectPublicDataUpdate = response.data;
           // alert(JSON.stringify(response.data));           
            $("#divPreLoad").hide();

        }, function (responseError) {                      
            $("#divPreLoad").hide();
            alert(serviceErrorMessege);
        }).finally(function () {
            $("#divPreLoad").hide();
           
        });
    }


    //==============================================================brodcaste for get projectPhase 

    $scope.OnProjectClick = function (sProjectID) {
       
        $scope.$broadcast('OnProjectSelected', { message: sProjectID });
    }

    document.getElementById("inputProjectName").focus();

    //=================================================================create newProject()
    $scope.createNewProject = function () {       
        //divPreLoad show()       
        $("#divPreLoad").show();
        $('.bs-example-modal-sm').modal('hide');
        var sPersonID = document.getElementById("hndPersonID").value;
        var newProjectData = BuildWhizNgAppProjectService.postCreateProjectData($scope.inputProjectName, sPersonID);

        newProjectData.then(function (response) {

            $scope.newProjectDataUpdate = response.data;
            //alert(JSON.stringify(response.data));
            $("#divPreLoad").hide();
        }, function (responseError) {
            $("#divPreLoad").hide();
            alert(serviceErrorMessege);
            
        }).finally(function () {
            GetAllProjectDetails();
        });
    }

    //=================================================================launch Project
    $scope.launchProject = function (sProjectID) {
        $("#divPreLoad").show();

        var launchProjectData = BuildWhizNgAppProjectService.postLaunchProjectData(sProjectID);

        launchProjectData.then(function (response) {

            $scope.launchProjectDataUpdate = response.data;
            // alert(JSON.stringify(response.data));

            $("#divPreLoad").hide();

        }, function (responseError) {
            $("#divPreLoad").hide();
            alert(serviceErrorMessege);
        }).finally(function () {
            GetAllProjectDetails();

        });;
    }

    //=================================================================end Project
    $scope.endProject = function (sProjectID) {
        $("#divPreLoad").show();
        
        var endProjectData = BuildWhizNgAppProjectService.postEndProjectData(sProjectID);

        endProjectData.then(function (response) {
            $scope.endProjectDataUpdate = response.data;
            //alert(JSON.stringify(response.data));
            $("#divPreLoad").hide();
           
        }, function (responseError) {
            $("#divPreLoad").hide();
            alert(serviceErrorMessege);
        }).finally(function () {           
            GetAllProjectDetails();            

        });;
    }

    //=================================================================delete Project
    $scope.deleteProject = function (sProjectID) {
     
        $("#divPreLoad").show();

        var deleteProjectData = BuildWhizNgAppProjectService.postDeleteProjectData(sProjectID);

        deleteProjectData.then(function (response) {

            $scope.deleteProjectDataUpdate = response.data;
           // alert(JSON.stringify(response.data));
            $("#divPreLoad").hide();

        }, function (responseError) {
            $("#divPreLoad").hide();
            alert(serviceErrorMessege);
        }).finally(function () {
            GetAllProjectDetails();
           

        });
    }

    //=================================================================setStatusBorderColor
    $scope.setStatusBorderColor = function (sStatus) {
        if (sStatus == "defined")
            return "Yellow";

        else if (sStatus == "running")
            return "Green";

        else if (sStatus == "ready")
            return "Green";

        else if (sStatus == "wait-phase")
            return "Red";

        else if (sStatus == "waiting")
            return "Magenta";

        else if (sStatus == "ended")
            return "Gray";

        else if (sStatus == "waiting2")
            return "Brown";

        else
            return "Magenta";
    }
      
    //=================================================================setStatusBackgroundColor
    $scope.setStatusBackgroundColor = function (sStatus) {
        if (sStatus == "defined")
            return "Yellow";

        else if (sStatus == "running")
            return "Green";

        else if (sStatus == "ready")
            return "Green";

        else if (sStatus == "wait-phase")
            return "Red";

        else if (sStatus == "waiting")
            return "Magenta";

        else if (sStatus == "ended")
            return "Gray";

        else if (sStatus == "waiting2")
            return "Brown";

        else
            return "Magenta";
    }

})