app.controller("BuildWhizNgAppProcessCtrl", function ($scope, BuildWhizNgAppProcessService) {
    //==============================open timer div 
    //$scope.IsVisibleDivTimer = false;
    //$scope.ShowHideDivTimer = function (idx) {        
    //    $scope.IsVisibleDivVariable = false;
    //    alert("$scope.selectedIndex" + $scope.selectedIndex);
    //    alert(idx);
    //    $scope.selectedIndex = idx;
      
    //    $scope.IsVisibleDivTimer = $scope.IsVisibleDivTimer ? false : true;
        
         
    //}
    //$scope.IsVisibleDivVariable = false;
    //$scope.ShowHideDivVariable = function () {
    //    //alert(idx);
    //    //$scope.IsVisibleDivVariable = $scope.IsVisibleDivVariable ? false : true;
    //    $scope.IsVisibleDivVariable = $scope.IsVisibleDivVariable === true ? false : true;
    //}




    $scope.clickBtnVariable = function (process) {
        angular.forEach($scope.PhaseProcessDataUpdate, function (currentItem) {
            currentItem.showVariableDiv = currentItem === process && !currentItem.showVariableDiv;        
            currentItem.showTimerDiv = false;
        });
        
    };
    $scope.clickBtnTimer = function (process) {
        angular.forEach($scope.PhaseProcessDataUpdate, function (currentItem) {
            currentItem.showTimerDiv = currentItem === process && !currentItem.showTimerDiv;            
            currentItem.showVariableDiv = false;
        });
        
    };







    //==========================GetAllPhaseActivityDetails() call from boradcast controllerPhase
    $scope.$on('OnPhaseSelected', function (event, args) {

        $scope.message = args.message;
        GetPhaseProcessDetails($scope.message);


    });

    //==========================GetPhaseProcessDetails() 
    function GetPhaseProcessDetails(sPhaseID) {
        $("#divPreLoad").show();
        var sPersonID = document.getElementById("hndPersonID").value;
        var PhaseProcessData = BuildWhizNgAppProcessService.getPhaseProcessData(sPhaseID, sPersonID);

        PhaseProcessData.then(function (response) {
            $scope.PhaseProcessDataUpdate = response.data;
            //alert(JSON.stringify(response.data));
            $("#divPreLoad").hide();

        }, function (responseError) {
            $("#divPreLoad").hide();
            alert(serviceErrorMessege);
        });
    };

    $scope.OnProcessClick = function (sPhaseID, sProcessBpmnName) {
        $scope.$broadcast('OnProcessSelected', { messagePhaseID: sPhaseID, messageBpmnName: sProcessBpmnName });
    }

    //==========================setProcessTimerDuration() 

    $scope.setProcessTimerDuration = function (sPhaseID, sTimerName, sProcessBpmnName, sTimerDuration) {
        //alert(sTimerDuration);
        var sTimerDurationField = sTimerDuration;

        if (sTimerDurationField.trim() != null && sTimerDurationField.trim() != "" && sTimerDurationField.trim() != "undefined") {

            var processTimerDurationData = BuildWhizNgAppProcessService.setProcessTimerDurationData(sPhaseID, sTimerName, sProcessBpmnName, sTimerDuration);
            $("#divPreLoad").show();
            processTimerDurationData.then(function (response) {
                $scope.timerDurationDataUpdate = response.data;
                //alert(JSON.stringify(response.data));
               
                $("#divPreLoad").hide();


            }, function (responseError) {
                $("#divPreLoad").hide();
                alert(serviceErrorMessege);

            }).finally(function () {
                $("#divPreLoad").hide();                
                GetPhaseProcessDetails(sPhaseID);
                $scope.inputModelTimerDuration = "";
            });

        }
        else {
            alert("Enter Timer Duration.")
        }

    }


    //==========================setProcessVariableValues() 

    $scope.setProcessVariableValue = function (sPhaseID, sVariableName, sProcessBpmnName, sVariableValue) {
        //alert(sTimerDuration);
        var sVariableValueField = sVariableValue;

        if (sVariableValueField.trim() != null && sVariableValueField.trim() != "" && sVariableValueField.trim() != "undefined") {

            var processVariableValueData = BuildWhizNgAppProcessService.setProcessVariableData(sPhaseID, sVariableName, sProcessBpmnName, sVariableValue);
            $("#divPreLoad").show();
            processVariableValueData.then(function (response) {
                $scope.variableDataUpdate = response.data;
                //alert(JSON.stringify(response.data));
                
                $("#divPreLoad").hide();

            }, function (responseError) {
                $("#divPreLoad").hide();
                alert(serviceErrorMessege);
            }).finally(function () {
                $("#divPreLoad").hide();
                GetPhaseProcessDetails(sPhaseID);
                $scope.inputModelTimerDuration = "";
            });

        }
        else {
            alert("Enter Timer Duration.")
        }

    }
    $scope.btnDiagramClick = function (sProcessBpmnName) {
        $('#modalDiagram').modal('show');
        var image = document.getElementById('imgBpmnDiagram');
        image.src = "baf/PhaseBpmnImage?bpmn_name=" + sProcessBpmnName;
        document.getElementById("txtBpmnDiagram").innerHTML = sProcessBpmnName;


    }


});

