app.controller("BuildWhizNgAppPhaseCtrl", function ($scope, BuildWhizNgAppPhaseService) {
    //alert("Phase ctrl load");



    var gprojectID = "";

    //var destroyOnProjectSelected;
    //destroyOnProjectSelected = $scope.$on('OnProjectSelected', function () { });

    //==============================================================getAll projectPhase()
    var OnProjectSelectedHandle = $scope.$on('OnProjectSelected', function (event, args) {

        gprojectID = args.message;
        $scope.message = args.message;
        GetProjectPhaseDetails($scope.message);
        //destroyOnProjectSelected();
    });


    function GetProjectPhaseDetails(sProjectID) {
        var sPersonID = document.getElementById("hndPersonID").value;
        var ProjectPhaseData = BuildWhizNgAppPhaseService.getProjectPhaseData(sProjectID, sPersonID);

        $("#divPreLoad").show();

        ProjectPhaseData.then(function (response) {
            $scope.projectPhaseDataUpdate = response.data;
            //alert("GetProjectPhaseDetails" + JSON.stringify(response.data));

            
            $("#divPreLoad").hide();

        }, function (responseError) {
            $("#divPreLoad").hide();
            alert(serviceErrorMessege);
        });
    };
    //==============================================================brodcast for get PhaseActivity from controllerProject 

    $scope.OnPhaseClick = function (sPhaseID) {
        $scope.$broadcast('OnPhaseSelected', { message: sPhaseID });
      
    }

    //==============================================================add Phase ()
    $scope.addPhase = function (project, phaseName) {
        
        if ($scope.selectPhaseNamesModel != "") {
        $("#divPreLoad").show();
        var sPhaseName = phaseName;
        var sPersonID = document.getElementById("hndPersonID").value;
        var sProjectID = $scope.project._id;

        var GetAddPhaseDetailsData = BuildWhizNgAppPhaseService.postAddPhaseData(sPhaseName, sProjectID, sPersonID);

        GetAddPhaseDetailsData.then(function (response) {
            $scope.addPhaseUpdate = response.data;
            //alert(JSON.stringify(response.data));

            $("#divPreLoad").show();            

        }, function (responseError) {

            $("#divPreLoad").hide();
            alert(serviceErrorMessege);

        }).finally(function () {

            $("#divPreLoad").hide();            
            $scope.message = sProjectID;
            GetProjectPhaseDetails($scope.message);
            //toggleOpenProject("agProjects", sProjectID);
            
        });
        }
        else {
            alert("Select Phase First");
        }

    }

    //===Toggle open project ===============================================================
    function toggleOpenProject (sAccordianID, sProjectID) {

        alert(child.getElementById("hdnProjectID").value);
        alert(sProjectID);

        var id = sAccordianID;
        var elements = angular.element($document[0].querySelector('#' + id));
        var children = elements.children();

        alert(children.length);
        

        for (var i = 0; i < children.length; i++) {

            var child = angular.element(children[i]);

            if (child.getElementById("dvProjectID").innerHTML == sProjectID) {
                if (child.hasClass('panel-collapse')) {
                    child.addClass('in');
                    child.removeClass('collapse');
                    child.css('height', 'auto');
                }
            }

        }

    };

    //==============================================================launch Phase
    $scope.launchPhase = function (sProjectID, sPhaseID, sPhaseBpmnName) {
        $("#divPreLoad").show();
        
        var launchPhaseData = BuildWhizNgAppPhaseService.postLaunchPhaseData(sProjectID, sPhaseID, sPhaseBpmnName);
        launchPhaseData.then(function (response) {

            $scope.launchPhaseDataUpdate = response.data;
            //alert(JSON.stringify(response.data));

            $("#divPreLoad").hide();
            
        }, function (responseError) {
            $("#divPreLoad").hide();
            alert(serviceErrorMessege);
        }).finally(function () {
            $("#divPreLoad").hide();            
            GetProjectPhaseDetails(sProjectID);
            $scope.$emit('OnProjectRefresh', { message: sProjectID });
        });
    }


    //=================================================================delete PhaseProject
    $scope.deletePhase = function (sProjectID, sPhaseID) {
        $("#divPreLoad").show();
        var deletePhaseData = BuildWhizNgAppPhaseService.postDeletePhaseData(sPhaseID);
        deletePhaseData.then(function (response) {
            $scope.deletePhaseDataUpdate = response.data;
            // alert(JSON.stringify(response.data));
            $("#divPreLoad").hide();
            
        }, function (responseError) {
            $("#divPreLoad").hide();
            alert(serviceErrorMessege);
        }).finally(function () {
            $("#divPreLoad").hide();
            GetProjectPhaseDetails(sProjectID);
        });
    }

    //==============================================================set Phase Admin
    $scope.setPhaseAdministrator = function (sProjectID, sPhaseID, sSelectedPersonID) {
        $("#divPreLoad").show();
        
        var setPhaseData = BuildWhizNgAppPhaseService.setPhaseAdministratorData(sProjectID, sPhaseID, sSelectedPersonID);

        setPhaseData.then(function (response) {
            $scope.setPhaseDataUpdate = response.data;
            //alert(JSON.stringify(response.data));
            $("#divPreLoad").hide();            
            
        }, function (responseError) {
            $("#divPreLoad").hide();
            alert(serviceErrorMessege);
        }).finally(function () {
            $("#divPreLoad").hide();            
            GetProjectPhaseDetails(sProjectID);           

        });
    };
});


