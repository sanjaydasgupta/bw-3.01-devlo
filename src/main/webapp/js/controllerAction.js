app.controller("BuildWhizNgAppActionCtrl", function ($scope, $http, BuildWhizNgAppActionService) {
   // alert("action ctrl load");


    //==========================GetAllActivityActionDetails() call from boradcast controllerActivity
    $scope.$on('OnActivitySelected', function (event, args) {
        $scope.message = args.message;
        GetAllActivityActionDetails($scope.message);        

    });

    $scope.callbackFunction = function () {

        GetAllActivityActionDetails($scope.message);       
    };

    //==========================GetAllActivityActionDetails()    
    function GetAllActivityActionDetails(sActivityID) {
       
        var sPersonID = document.getElementById("hndPersonID").value;
        var activityActionData = BuildWhizNgAppActionService.getActivityActionData(sActivityID, sPersonID);

        $("#divPreLoad").show();

        activityActionData.then(function (response) {
            $scope.activityActionDataUpdate = response.data;
            //alert(JSON.stringify(response.data));
            $(".msgToggle").hide();
            $("#divPreLoad").hide();
        }, function (responseError) {
            $(".msgToggle").show();
            document.getElementById('dvPhaseMessege').innerHTML = serviceErrorMessege;
            $("#divPreLoad").hide();
        });
    };


    //==========================Add Action ()    
    $scope.btnAddAction = function (sActivityID, sActionName, sActionType, sActivityBpmnName) {
        
        if (sActionName.trim() != '' && sActionName.trim() != 'undefined' && sActionType != '' && sActionType != 'undefined') {

            var sPersonID = document.getElementById("hndPersonID").value;
            var addActionData = BuildWhizNgAppActionService.postAddActionData(sActivityID, sActionName, sActionType, sPersonID, sActivityBpmnName);

            $("#divPreLoad").show();

            addActionData.then(function (response) {
                $scope.AddActionDataUpdate = response.data;
                //alert(JSON.stringify(response.data));

                $("#divPreLoad").hide();

                // set the default/blank value of select and input
                $scope.inputActionName = '';
                $scope.ddlAddActionType = '';

              
            }, function (responseError) {
                $("#divPreLoad").hide();
                alert(serviceErrorMessege);

            }).finally(function () {

                $scope.message = sActivityID;
                GetAllActivityActionDetails($scope.message);

                $("#divPreLoad").hide();
                $scope.inputReviewName = "";
            });


        }
        else {
            if (sActionName == 'undefined' || sActionName != '') {
                alert("Enter Action Name.");
            }
            else {
                alert("Select Action Type.");
            }
        }
    }

    //==========================set ActionDuration()    
    $scope.setActionDuration = function (sActivityID, sActionName, sActionDuration) {
        var sInputActionDurationField = sActionDuration;
        if (sInputActionDurationField.trim() != "") {

            var actionDurationData = BuildWhizNgAppActionService.setActionDurationData(sActivityID, sActionName, sActionDuration);
            $("#divPreLoad").show();
            actionDurationData.then(function (response) {
                $scope.actionDurationDataUpdate = response.data;
                // alert(JSON.stringify(response.data));
                
                $("#divPreLoad").hide();


            }, function (responseError) {
                $("#divPreLoad").hide();
                alert(serviceErrorMessege);

            }).finally(function () {
                $("#divPreLoad").hide();
                $scope.message = sActivityID;
                GetAllActivityActionDetails($scope.message);                
                $scope.inputModelActionDuration = "";
            });

        }
        else {
            alert("Enter Action Duration.")
        }

    }


    //==========================set ActionAdministrator()    
    $scope.setActionAdministrator = function (sActivityID, sProjectID, sActionName, sAssignPersonID) {

        var actionAdministratorData = BuildWhizNgAppActionService.setActionAdministratorData(sActivityID, sProjectID, sActionName, sAssignPersonID);

        $("#divPreLoad").show();

        actionAdministratorData.then(function (response) {
            $scope.actionAdministratorDataUpdate = response.data;
             //alert(JSON.stringify(response.data));

            $("#divPreLoad").hide();

        }, function (responseError) {
            $("#divPreLoad").hide();
            alert(serviceErrorMessege);

        }).finally(function () {
            $("#divPreLoad").hide();
            $scope.message = sActivityID;
            GetAllActivityActionDetails($scope.message);
            
            
        });


    }

    //==========================ActionComplete()    
    $scope.actionComplete = function (sProjectID, sPhaseID, sProcessBpmnName, sActionName, sActivityID, sActionType, sActionReview_ok) {

        if ((sActionReview_ok != "" && sActionReview_ok != "undefined") || sActionType != 'review') {
            var actionCompleteData = BuildWhizNgAppActionService.postActionCompleteData(sActionName, sActivityID, sActionType, sActionReview_ok);
            $("#divPreLoad").show();
            actionCompleteData.then(function (response) {
                $scope.actionCompleteDataUpdate = response.data;
                // alert(JSON.stringify(response.data));
                
                $("#divPreLoad").hide();

                $scope.$emit('OnProjectRefresh', { message: sProjectID });
                //$scope.$emit('OnProjectSelected', { message: sProjectID });
                //$scope.$emit('OnPhaseSelected', { message: sPhaseID });
                //$scope.$emit('OnProcessSelected', { messagePhaseID: sPhaseID, messageBpmnName: sProcessBpmnName });
                //GetAllActivityActionDetails(sActivityID);

                //var key = "";
                //var value = "";
                //for (var keyName in $scope.actionCompleteDataUpdate) {
                //    key = keyName;
                //    value = $scope.actionCompleteDataUpdate[keyName];
                //    value = value.toString().trim();
                   
                //    if (key == "all_processes_complete" && value == "true") {                       
                //        $scope.$emit('OnProjectSelected', { message: sProjectID });

                //    }

                //    else if (key == "all_activities_complete" && value == "true") {
                        
                //        $scope.$emit('OnPhaseSelected', { message: sPhaseID });                       
                //    }

                //    else if (key == "all_actions_complete" && value == "true") {
                        
                //        $scope.$emit('OnProcessSelected', { messagePhaseID: sPhaseID, messageBpmnName: sProcessBpmnName });                        
                       
                //    }

                //    else {
                //        GetAllActivityActionDetails(sActivityID);

                //    }
                //}
                
               
            }, function (responseError) {
                $("#divPreLoad").hide();
                alert(serviceErrorMessege);
            }).finally(function () {
                $("#divPreLoad").hide();
            });
        }
        else {
            alert("Select Review Result.");
        }


    }
    //==========================uploadDisabled()    
    $scope.uploadDisabled = function (project, action) {
        if (project.status != 'running') {
            return true;
        } else if (action.type == 'prerequisite') {
            return action.status == 'ended' || action.status == 'ready';
        } else if (action.type == 'main') {
            return action.status != 'waiting';
        } else if (action.type == 'review') {
            return action.status != 'waiting';
        } else {
            return true;
        }
    }
    //==========================actionCompleteDisabled ()    

    $scope.actionCompleteDisabled = function (project, action) {
     
        var bStatus = true;
        if (project.status != 'running' || !action.is_ready) {
            bStatus = true;
        } else if (action.type == 'prerequisite') {
            bStatus = (action.status == 'ended' || action.status == 'ready');
        } else if (action.type == 'main') {
            bStatus = (action.status != 'waiting');
        } else if (action.type == 'review') {
            bStatus = (action.status != 'waiting');
            if (!bStatus) {
                if (action.review_ok == '' || action.review_ok =='undefined') {
                    bStatus = true;
                }
            }
          
        } else {
            bStatus = true;
        }
        return bStatus;
    }
  
    //==========================reviewStatusDisabled ()
    $scope.reviewStatusDisabled = function (project, action) {

        var bStatus = true;
        if (project.status != 'running' || !action.is_ready) {
            bStatus = true;
        } else if (action.type == 'prerequisite') {
            bStatus = (action.status == 'ended' || action.status == 'ready');
        } else if (action.type == 'main') {
            bStatus = (action.status != 'waiting');
        } else if (action.type == 'review') {
            bStatus = (action.status != 'waiting');           
        } else {
            bStatus = true;
        }
        return bStatus;

    }





    
});


app.directive("bwUpload", ['$log', '$http',  function ($log, $http) {
    return {

        scope: { project: "=", activity: "=", action: "=", document: "=", callbackFunction: '&' },

        link: function (scope, element, attributes) {
            element.bind("change", function (changeEvent) {
                var reader = new FileReader();
                reader.onload = function (loadEvent) {
                    $log.log('onload.byteLength: ' + loadEvent.target.result.byteLength);                    
                    scope.$apply(function () {                        
                        var params = 'project_id=' + scope.project._id + '&activity_id=' + scope.activity._id +
                              '&action_name=' + scope.action.name + '&document_id=' + scope.document._id

                        $("#divPreLoad").show();

                        var config = {
                            
                            url: 'baf/DocumentUpload?' + params,
                            method: 'POST',
                            headers: { 'Content-Type': 'application/octet-stream' },
                            data: new Uint8Array(loadEvent.target.result),
                            transformRequest: []
                        };
                        
                        $http(config).then(
                          function (resp) {
                              var response = resp.data;
                              $log.log("DocumentUpload response (fileName, length): " + response.fileName + ", " + response.length);

                              $("#divPreLoad").hide();

                              //alert('Document Uploded.');

                              scope.callbackFunction()(scope.activity._id);                           
                          },
                          function (responseError) {

                              $(".msgToggle").show();
                              document.getElementById('lblMsg').innerHTML = serviceErrorMessege;

                          }
                        );
                    });
                    
                }
                reader.readAsArrayBuffer(changeEvent.target.files[0]);
            });
        }
    }
}]);




