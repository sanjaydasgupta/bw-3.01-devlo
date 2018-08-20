app.controller("BuildWhizNgAppActivityCtrl", function ($scope, BuildWhizNgAppActivityService) {
    // alert("activity ctrl load");

    //==========================GetAllPhaseActivityDetails() call from boradcast controllerPhase
    $scope.$on('OnProcessSelected', function (event, args) {      
        $scope.messagePhaseID = args.messagePhaseID;
        $scope.messageBpmnName = args.messageBpmnName;
        GetAllProcessActivityDetails($scope.messagePhaseID, $scope.messageBpmnName);
        
        
      
    });
    //==========================Define GetAllPhaseActivityDetails()
    function GetAllProcessActivityDetails(sPhaseID, sProcessBpmnName) {
        var sPersonID = document.getElementById("hndPersonID").value;

        var processActivityData = BuildWhizNgAppActivityService.getProcessActivityData(sPhaseID, sPersonID, sProcessBpmnName);
        
        $("#divPreLoad").show();

        processActivityData.then(function (response) {
            $scope.phaseActivityDataUpdate = response.data;
             // alert(JSON.stringify(response.data));
            $("#divPreLoad").hide();
            $(".msgToggle").hide();

            if ($scope.phaseActivityDataUpdate.length <= 0) {
                $(".msgToggle").show();
                //document.getElementById('dvPhaseMessege').innerHTML = "No Activity found.";
            }
        }, function (responseError) {
            $("#divPreLoad").hide();
            alert(serviceErrorMessege);
            
        }).finally(function () {
            $("#divPreLoad").hide();
        });;
    };

    //==============================================================brodcast for get ActivityAction from controllerAction 

    $scope.OnActivityClick = function (sActivityID) {
     
        $scope.$broadcast('OnActivitySelected', { message: sActivityID });
    }

    
})



