var buildWhizApp = angular.module('buildWhizApp', [])


buildWhizApp.factory('bwGlobals', [function() {
  var globals = {
    form: '',
    refreshView: null
  }
  return globals;
}])


buildWhizApp.controller('BuildWhizCtrl', ['$http', '$log', 'bwGlobals', function($http, $log, bwGlobals) {
  var self = this;
  self.selections = {}
  self.persons = [];
  self.personsById = {};
  self.personId = null;
  self.projects = [];
  self.phaseNames = [];
  self.bwGlobals = bwGlobals;
  self.displayDetails = {}
  $log.log('calling GET api/Person')
  $http.get('api/Person').then(
    function(resp) {
      self.persons = resp.data
      self.persons.forEach(function(p) {self.personsById[p._id] = p;});
    },
    function(errResponse) {alert("ERROR(persons): " + errResponse);}
  );
  $http.get('baf/PhaseBpmnNamesFetch').then(
    function(resp) {
      self.phaseNames = resp.data
      $log.log('Phase-Names: ' + self.phaseNames)
    },
    function(errResponse) {alert("ERROR(phase-names): " + errResponse);}
  );
  self.personTabColor = function(person) {
    if (person._id == self.personId) {
      return 'cyan';
    } else {
      return 'white';
    }
  }
  self.refreshView = function() {
    $log.log('refreshView() called')
    self.personSelected(self.personsById[self.personId]);
  }
  bwGlobals.refreshView = self.refreshView;
  self.personSelected = function(person) {
    $log.log('personSelected() called')
    self.personId = person._id;
    var query = 'person_id=' + person._id;
    $log.log('calling GET baf/OwnedProjects?' + query)
    $http.get('baf/OwnedProjects?' + query).then(
      function(resp) {
        self.projects = resp.data;
      },
      function(errResponse) {$log.log('GET baf/OwnedProjects ERROR: ' + errResponse);}
    );
  }
  self.projectSetPublic = function(project) {
    var query = 'project_id=' + project._id + '&public=' + project.public;
    $log.log('calling POST baf/ProjectSetPublic?' + query)
    $http.post('baf/ProjectSetPublic?' + query).then(
      function(resp) {
        //alert('Set Public Successful');
      },
      function(errResponse) {$log.log('POST baf/ProjectSetPublic ERROR: ' + errResponse);}
    );
  }
  self.projectSelectionChange = function(project, personId) {
    if (project.displayDetails) {
      var query = 'person_id=' + personId + '&project_id=' + project._id;
      $log.log('calling GET baf/OwnedPhases?' + query)
      $http.get('baf/OwnedPhases?' + query).then(
        function(resp) {
          project.phases = resp.data;
        },
        function(errResponse) {$log.log('GET baf/OwnedPhases ERROR: ' + errResponse);}
      );
    } else {
      project.phases = [];
    }
  }
  self.phaseSelectionChange = function(phase, personId) {
    if (phase.displayDetails) {
      var query = 'person_id=' + personId + '&phase_id=' + phase._id;
      $log.log('calling GET baf/OwnedProcesses?' + query)
      $http.get('baf/OwnedProcesses?' + query).then(
        function(resp) {
          phase.processes = resp.data;
        },
        function(errResponse) {$log.log('GET baf/OwnedProcesses ERROR: ' + errResponse);}
      );
    } else {
      phase.processes = [];
    }
  }
  self.processSelectionChange = function(phase, process, personId) {
    if (process.displayDetails) {
      var query = 'person_id=' + personId + '&phase_id=' + phase._id + '&bpmn_name=' + process.bpmn_name;
      $log.log('calling GET baf/OwnedActivities?' + query)
      $http.get('baf/OwnedActivities?' + query).then(
        function(resp) {
          process.activities = resp.data;
        },
        function(errResponse) {$log.log('GET baf/OwnedActivities ERROR: ' + errResponse);}
      );
    } else {
      process.activities = [];
    }
  }
  self.activitySelectionChange = function(activity, personId) {
    if (activity.displayDetails) {
      var query = 'person_id=' + personId + '&activity_id=' + activity._id;
      $log.log('calling GET baf/OwnedActions?' + query)
      $http.get('baf/OwnedActions?' + query).then(
        function(resp) {
          activity.actions = resp.data;
        },
        function(errResponse) {$log.log('GET baf/OwnedActions ERROR: ' + errResponse);}
      );
    } else {
      activity.actions = [];
    }
  }
  self.sampleProjectSetup = function() {
    $log.log('Calling POST tools/MainProgramLauncher?program=SampleProjectSetup')
    $http.post('tools/MainProgramLauncher?program=SampleProjectSetup').then(
      function(resp) {},
      function(errResponse) {alert(errResponse);}
    );
  }
  self.personRecordsRedo = function() {
    $log.log('Calling POST tools/MainProgramLauncher?program=PersonRecordsRedo')
    $http.post('tools/MainProgramLauncher?program=PersonRecordsRedo').then(
      function(resp) {},
      function(errResponse) {alert(errResponse);}
    );
  }
  self.documentRecordsRedo = function() {
    $log.log('Calling POST tools/MainProgramLauncher?program=DocumentRecordsRedo')
    $http.post('tools/MainProgramLauncher?program=DocumentRecordsRedo').then(
      function(resp) {},
      function(errResponse) {alert(errResponse);}
    );
  }
  self.createNewProject = function() {
    var p = '{"name": "' + self.newProjectName + '", "admin_person_id": ObjectId("' + self.personId + '")}';
    $log.log('calling POST api/Project/ with ' + p)
    $http.post('api/Project/', p).then(
      function(resp) {
        var projects = self.projects;
        var project = resp.data;
        project.displayDetails = true;
        projects.push(project);
        self.projects = projects;
      },
      function(errResponse) {alert(errResponse);}
    );
  }
  self.expandAll = function() {
    self.projects.forEach(function (p) {p.displayDetails = self.expandAllSelection;})
  }
  self.statusColor = function(status) {
    switch (status) {
      case 'defined':
        return 'yellow';
      case 'running':
        return 'green';
      case 'ready':
        return 'green';
      case 'wait-phase':
        return 'red';
      case 'waiting':
        return 'magenta';
      case 'waiting2':
        return 'brown';
      case 'ended':
        return 'gray'
      default:
        return 'magenta';
    }
  }
}])


buildWhizApp.controller('CreateProjectCtrl', ['$http', '$log', 'bwGlobals', function($http, $log, bwGlobals) {
  var self = this;
  self.organizations = [];
  self.orgEmployees = [];
  self.project = null;
  $log.log('calling GET api/Organization')
  $http.get('api/Organization').then(
    function(resp) {
      self.organizations = resp.data
    },
    function(errResponse) {alert("ERROR(orgs): " + errResponse);}
  );
  self.projectOrganizationChanged = function() {
    var query = 'organization_id=' + self.project.organization_id;
    $log.log('calling GET api/Person with ' + query)
    $http.get('api/Person?' + query).then(
      function(resp) {
        self.orgEmployees = resp.data
      },
      function(errResponse) {alert("ERROR(personsByOrganizationId): " + errResponse);}
    );
  }
}])


buildWhizApp.controller('ProjectManageCtrl', ['$http', '$log', 'bwGlobals', function($http, $log, bwGlobals) {
  var self = this;
  self.phaseName = null;
  self.launchProject = function(project) {
    var p = 'project_id=' + project._id;
    $log.log('calling POST baf/ProjectLaunch?' + p)
    $http.post('baf/ProjectLaunch?' + p).then(
      function(resp) {},
      function(errResponse) {alert(errResponse);}
    );
  }
  self.deleteProject = function(project) {
    var p = project._id;
    $log.log('calling DELETE api/Project/' + p)
    $http.delete('api/Project/' + p).then(
      function(resp) {alert('Delete project successful'); },
      function(errResponse) {'Delete error: ' + alert(errResponse);}
    );
  }
  self.deletePhase = function(phase) {
    var pid = phase._id;
    $log.log('calling DELETE api/Phase/' + pid)
    $http.delete('api/Phase/' + pid).then(
      function(resp) {alert('Delete phase successful'); },
      function(errResponse) {'Delete error: ' + alert(errResponse);}
    );
  }
  self.endProject = function(project) {
    var p = '&project_id=' + project._id;
    $log.log('calling POST baf/ProjectEnd?' + p)
    $http.post('baf/ProjectEnd?' + p).then(
      function(resp) {},
      function(errResponse) {'Delete error: ' + alert(errResponse);}
    );
  }
  self.addPhase = function(project) {
    var p = 'phase_name=' + project.newPhaseName + '&project_id=' + project._id +
        '&admin_person_id=' + project.admin_person_id;
    $log.log('calling POST baf/PhaseAdd?' + p)
    $http.post('baf/PhaseAdd?' + p).then(
      function(resp) {
        if (project.phases) {
          var phases = project.phases;
          phases.push(resp.data);
          project.phases = phases;
        } else {
          project.phases = [resp.data];
        }
      },
      function(errResponse) {alert(errResponse);}
    );
  }
  self.addReviewAction = function(activity, personId) {
    if (activity.newReviewName) {
      var p = 'action_name=' + activity.newReviewName + '&activity_id=' + activity._id +
          '&type=review' + '&assignee_id=' + personId + '&bpmn_name=' + activity.bpmn_name;
      $log.log('calling POST baf/ActionAdd?' + p);
      $http.post('baf/ActionAdd?' + p).then(
        function(resp) {alert('Add review-action success'); },
        function(errResponse) {alert(errResponse);}
      );
    } else {
      alert('Invalid action name');
    }
  }
  self.addPrerequisiteAction = function(activity, personId) {
    if (activity.newPrerequisiteName) {
      var p = 'action_name=' + activity.newPrerequisiteName + '&activity_id=' + activity._id +
          '&type=prerequisite' + '&assignee_id=' + personId + '&bpmn_name=' + activity.bpmn_name;
      $log.log('calling POST baf/ActionAdd?' + p);
      $http.post('baf/ActionAdd?' + p).then(
        function(resp) {alert('Add prerequisite-action success'); },
        function(errResponse) {alert(errResponse);}
      );
    } else {
      alert('Invalid action name');
    }
  }
  self.launchPhase = function(project, phase) {
    var p = 'project_id=' + project._id + '&phase_id=' + phase._id + '&phase_bpmn_name=' + phase.bpmn_name;
    $log.log('calling POST baf/PhaseLaunch?' + p)
    $http.post('baf/PhaseLaunch?' + p).then(
      function(resp) {},
      function(errResponse) {alert(errResponse);}
    );
  }
  self.setActionDuration = function(activity, action) {
    if (action.duration) {
      var p = 'activity_id=' + activity._id + '&action_name=' + action.name + '&duration=' + action.duration;
      $log.log('calling POST baf/ActionDurationSet?' + p)
      $http.post('baf/ActionDurationSet?' + p).then(
        function(resp) {alert('Set action duration successful'); },
        function(errResponse) {alert(errResponse);}
      );
    } else {
      alert('Invalid duration');
    }
  }
  self.setTimerDuration = function(phase, timer) {
    var p = 'phase_id=' + phase._id + '&timer_name=' + timer.name + '&bpmn_name=' + timer.bpmn_name + '&duration=' + timer.duration;
    $log.log('calling POST baf/TimerDurationSet?' + p)
    $http.post('baf/TimerDurationSet?' + p).then(
      function(resp) {alert('Set timer duration successful'); },
      function(errResponse) {alert(errResponse);}
    );
  }
  self.setVariableValue = function(phase, variable) {
    var p = 'phase_id=' + phase._id + '&label=' + variable.label + '&bpmn_name=' + variable.bpmn_name + '&value=' + variable.value;
    $log.log('calling POST baf/VariableValueSet?' + p)
    $http.post('baf/VariableValueSet?' + p).then(
      function(resp) {alert('Set variable value successful'); },
      function(errResponse) {alert(errResponse);}
    );
  }
  self.optionSelected = function(action, person) {
    if (action.assignee_person_id == person._id) {
      return 'selected';
    } else {
      return '';
    }
  }
  self.setActionAssignee = function(project, activity, action) {
    var p = 'activity_id=' + activity._id + '&project_id=' + project._id + '&action_name=' + action.name +
        '&person_id=' + action.assignee_person_id;
    $log.log('calling POST baf/ActionContributorSet?' + p)
    $http.post('baf/ActionContributorSet?' + p).then(
      function(resp) {alert('Set assignee successful'); },
      function(errResponse) {alert(errResponse);}
    );
  }
  self.setPhaseAdministrator = function(project, phase) {
    var p = 'project_id=' + project._id + '&phase_id=' + phase._id + '&person_id=' + phase.admin_person_id;
    $log.log('calling POST baf/PhaseAdministratorSet?' + p)
    $http.post('baf/PhaseAdministratorSet?' + p).then(
      function(resp) {alert('Set phase-administrator successful'); },
      function(errResponse) {alert(errResponse);}
    );
  }
  self.completeAction = function(activity, action) {
    var p = 'activity_id=' + activity._id + '&action_name=' + action.name;
    if (action.type == 'review') {
      p += '&review_ok=' + action.review_ok;
    }
    $log.log('calling POST baf/ActionComplete?' + p)
    $http.post('baf/ActionComplete?' + p).then(
      function(resp) {},
      function(errResponse) {alert(errResponse);}
    );
  }
  self.uploadDisabled = function(project, action) {
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
  self.actionCompleteDisabled = function(project, action) {
    if (project.status != 'running' || !action.is_ready) {
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
}])

buildWhizApp.directive("bwUpload", ['$log', '$http', 'bwGlobals', function($log, $http, bwGlobals) {
  return {
    scope: {project: "=", activity: "=", action: "=", document: "="},
    link: function (scope, element, attributes) {
      element.bind("change", function (changeEvent) {
        var reader = new FileReader();
        reader.onload = function (loadEvent) {
          $log.log('onload.byteLength: ' + loadEvent.target.result.byteLength);
          scope.$apply(function () {
            var p = 'project_id=' + scope.project._id + '&activity_id=' + scope.activity._id +
                  '&action_name=' + scope.action.name + '&document_id=' + scope.document._id
            $log.log('calling POST baf/DocumentUpload?' + p)
            var config = {
              url: 'baf/DocumentUpload?' + p,
              method: 'POST',
              headers: {'Content-Type': 'application/octet-stream'},
              data: new Uint8Array(loadEvent.target.result),
              transformRequest: []
            };
            $http(config).then(
              function(resp) {
                var response = resp.data;
                $log.log("DocumentUpload response (fileName, length): " + response.fileName + ", " + response.length);
                alert('Success, received ' + response.length + ' bytes')
                //bwGlobals.refreshView()
              },
              function(errResponse) {alert(errResponse);}
            );
          });
        }
        reader.readAsArrayBuffer(changeEvent.target.files[0]);
      });
    }
  }
}]);
