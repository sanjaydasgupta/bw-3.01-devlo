app.controller("BuildWhizNgAppPhaseCtrl", function ($scope, BuildWhizNgAppPhaseService) {


    //==============================================================getAll projectPhase()
    var OnProjectSelectedHandle = $scope.$on('OnProjectSelected', function (event, args) {
        var sProjectID = "";
        var bExpand = "";

        sProjectID = args.messegeProjectID;
        bExpand = args.messegeExpand;

        GetProjectPhaseDetails(sProjectID, bExpand);
    });


    function GetProjectPhaseDetails(sProjectID, bExpand) {
        var sPersonID = document.getElementById("hndPersonID").value;
        var ProjectPhaseData = BuildWhizNgAppPhaseService.getProjectPhaseData(sProjectID, sPersonID);

        $("#divPreLoad").show();

        ProjectPhaseData.then(function (response) {
            $scope.projectPhaseDataUpdate = response.data;
            //alert("GetProjectPhaseDetails" + JSON.stringify(response.data));          
            $("#divPreLoad").hide();

            if (bExpand) {

            }

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
    $scope.addPhase = function (phaseName) {

        if ($scope.selectPhaseNamesModel.trim() != "" || $scope.selectPhaseNamesModel.trim() !== undefined || $scope.selectPhaseNamesModel.trim() !== null) {
            $("#divPreLoad").show();
            var sPhaseName = phaseName;
            var sPersonID = document.getElementById("hndPersonID").value;
            var sProjectID = $scope.project._id;

            var GetAddPhaseDetailsData = BuildWhizNgAppPhaseService.postAddPhaseData(sPhaseName, sProjectID, sPersonID);

            GetAddPhaseDetailsData.then(function (response) {
                $scope.addPhaseUpdate = response.data;
                //alert(JSON.stringify(response.data));

                $("#divPreLoad").show();

                GetProjectPhaseDetails(sProjectID, true);

            }, function (responseError) {

                $("#divPreLoad").hide();
                alert(serviceErrorMessege);

            }).finally(function () {

                $("#divPreLoad").hide();

            });
        }
        else {
            alert("Please Select Phase");
        }

    }

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


