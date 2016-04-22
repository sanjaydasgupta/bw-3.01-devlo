
//====LogIn Service ====  
app.service("BuildWhizNgAppLogInService", function ($http) {
    // LogInService = {};    
    this.UserAuthenticate = function (email, password) {
        var response = $http({
            method: "POST",
            url: "baf/LoginPost",
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            params: {
                email: email,
                password: password
            }
        });

        return response;
    }
});

//*****************************************NextGen Services***************************************
app.service("BuildWhizNgAppNextGenService", function ($http) {
});

//*****************************************Dashboard Services***************************************
app.service("BuildWhizNgAppDashboardService", function ($http) {
});

//*****************************************Project Services*************************************** %%%%
app.service("BuildWhizNgAppProjectService", function ($http) {

    //==============get PhaseNames (for select box) service 
    this.getPhaseNamesData = function () {

        var response = $http.get('baf/PhaseBpmnNamesFetch');

        return response;
    }

    //==============get persons (for Administrator select box) service 
    this.getPersonsData = function () {

        var response = $http.get('api/Person');

        return response;
    }

    //==============get getProjectData service 
    this.getProjectData = function (sPersonID) {

        var params = 'person_id=' + sPersonID;

        return $http.get('baf/OwnedProjects?' + params);
    }

    //==============post createProjectData service 
    this.postCreateProjectData = function (sName, sPersonID) {

        var params = '{"name": "' + sName + '", "status": "created", ' + '"admin_person_id": ObjectId("' + sPersonID + '")}';
        var response = $http.post('api/Project/', params);

        return response;
    }

    //==============post launchedProjectData service 
    this.postLaunchProjectData = function (project_id) {

        var params = 'project_id=' + project_id;
        var response = $http.post('baf/ProjectLaunch?' + params);

        return response;
    }

    //==============post end ProjectData service 
    this.postEndProjectData = function (project_id) {

        var params = '&project_id=' + project_id;
        var response = $http.post('baf/ProjectEnd?' + params);

        return response;
    }

    //=============post delete ProjectData service 
    this.postDeleteProjectData = function (project_id) {

        var params = project_id;
        var response = $http.delete('api/Project/' + params);

        return response;
    }
    //=============post projectSetPublic service 
    this.postProjectSetPublicData = function (sProjectID, bChecked) {
        
        var params = 'project_id=' + sProjectID + '&public=' + bChecked;       
        var response = $http.post('baf/ProjectSetPublic?' + params);

        return response;
    }
});

//*****************************************Phase Services*************************************** %%%%
app.service("BuildWhizNgAppPhaseService", function ($http) {

    //==============get ProjectPhase service 
    this.getProjectPhaseData = function (sProjectID, sPersonID) {

        var params = 'project_id=' + sProjectID + '&person_id=' + sPersonID;

        return $http.get('baf/OwnedPhases?' + params);
    }

    //==============get Add Phase service 
    this.postAddPhaseData = function (sPhaseName, sProjectID, sPersonID) {

        var params = 'phase_name=' + sPhaseName + '&project_id=' + sProjectID + '&admin_person_id=' + sPersonID;
        var response = $http.post('baf/PhaseAdd?' + params);

        return response;
    }
 
    //==============setPhaseAdministrator service 
    this.setPhaseAdministratorData = function (sProjectID, sPhaseID, sSelectedPersonID) {

        var params = 'project_id=' + sProjectID + '&phase_id=' + sPhaseID + '&person_id=' + sSelectedPersonID;
        var response = $http.post('baf/PhaseAdministratorSet?' + params);

        return response;
    };

    //==============launchedPhaseData service 
    this.postLaunchPhaseData = function (sProjectID, sPhaseID, sPhaseBpmnName) {

        var params = 'project_id=' + sProjectID + '&phase_id=' + sPhaseID + '&phase_bpmn_name=' + sPhaseBpmnName;
        var response = $http.post('baf/PhaseLaunch?' + params);

        return response;
    };

    //=============post delete PhaseData service 
    this.postDeletePhaseData = function (sPhaseID) {

        var params = sPhaseID;       
        var response = $http.delete('api/Phase/' + params);

        return response;
    }
   
});

//*****************************************Activity Services*************************************** %%%%
app.service("BuildWhizNgAppActivityService", function ($http) {

    //==============get phaseActivity service 
    this.getProcessActivityData = function (sPhaseID, sPersonID, sProcessBpmnName) {

        var params = 'person_id=' + sPersonID + '&phase_id=' + sPhaseID + '&bpmn_name=' + sProcessBpmnName;
       
        return $http.get('baf/OwnedActivities?' + params);

    }

});

//*****************************************Action Services*************************************** %%%%
app.service("BuildWhizNgAppActionService", function ($http) {


    //==============get activityAction service
    this.getActivityActionData = function (sActivityID, sPersonID) {

        var params = 'person_id=' + sPersonID + '&activity_id=' + sActivityID;

        return $http.get('baf/OwnedActions?' + params);
    }

    //==============postAddActionData service
    this.postAddActionData = function (sActivityID, sActionName, sActionType, sPersonID, sActivityBpmnName) {

        var params = 'action_name=' + sActionName + '&activity_id=' + sActivityID + '&type=' + sActionType + '&assignee_id=' + sPersonID + '&bpmn_name=' + sActivityBpmnName;        
        var response = $http.post('baf/ActionAdd?' + params);

        return response;
    }

    ////==============post PrerequisiteData service
    //this.postPrerequisiteData = function (sActivityID, sPrerequisiteName, sPersonID, sActivityBpmnName) {

    //    var params = 'action_name=' + sPrerequisiteName + '&activity_id=' + sActivityID + '&type=prerequisite' + '&assignee_id=' + sPersonID + '&bpmn_name=' + sActivityBpmnName;
        
    //    var response = $http.post('baf/ActionAdd?' + params);
    //    return response;
    //}

    //==============set ActionDurationData service
    this.setActionDurationData = function (sActivityID, sActionName, sActionDuration) {

        var params = 'activity_id=' + sActivityID + '&action_name=' + sActionName + '&duration=' + sActionDuration;
        var response = $http.post('baf/ActionDurationSet?' + params);

        return response;
    }

    //==============set ActionAdministratorData service 
    this.setActionAdministratorData = function (sActivityID, sProjectID, sActionName, sAssignPersonID) {

        var params = 'activity_id=' + sActivityID + '&project_id=' + sProjectID + '&action_name=' + sActionName + '&person_id=' + sAssignPersonID;

        var response = $http.post('baf/ActionContributorSet?' + params);
        return response;
    }

    //==============get ActionCompleteData service 
    this.postActionCompleteData = function (sActionName, sActivityID, sActionType, sActionReview_ok) {
        
        var params = 'activity_id=' + sActivityID + '&action_name=' + sActionName;

        if (sActionType == 'review') {
            params += '&review_ok=' + sActionReview_ok;
        }
        var response = $http.post('baf/ActionComplete?' + params);
        return response;
    }

});

//*****************************************Details Services*************************************** %%%%
app.service("BuildWhizNgAppDetailsService", function ($http) {


});

//*****************************************Process Services*************************************** %%%%
app.service("BuildWhizNgAppProcessService", function ($http) {

    //==============get phaseActivity service 
    this.getPhaseProcessData = function (sPhaseID, sPersonID) {

        var params = 'person_id=' + sPersonID + '&phase_id=' + sPhaseID;

        return $http.get('baf/OwnedProcesses?' + params);
    }

    //==============set ProcessTimerDurationData service 
    this.setProcessTimerDurationData = function (sPhaseID, sTimerName, sProcessBpmnName, sTimerDuration) {

        var params = 'phase_id=' + sPhaseID + '&timer_name=' + sTimerName + '&bpmn_name=' + sProcessBpmnName + '&duration=' + sTimerDuration;
        var response = $http.post('baf/TimerDurationSet?' + params);

        return response;
    }

    //==============set ProcessVariableData service 
    this.setProcessVariableData = function (sPhaseID, sVariableName, sProcessBpmnName, sVariableValue) {

        var params = 'phase_id=' + sPhaseID + '&label=' + sVariableName + '&bpmn_name=' + sProcessBpmnName + '&value=' + sVariableValue;        
        var response = $http.post('baf/VariableValueSet?' + params);

        return response;
    }
});

