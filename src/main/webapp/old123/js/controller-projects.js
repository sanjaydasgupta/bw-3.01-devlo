﻿angular.module('BuildWhizApp')

.controller("ProjectsCtrl", ['$log', '$http', 'AuthenticationService', '$routeParams', '$window',
    function ($log, $http, AuthService, $routeParams, $window) {

  var self = this;
  self.busy = false;

  self.initialProjectId = $routeParams.hasOwnProperty("project_id") ? $routeParams.project_id : null;
  self.initialPhaseId = $routeParams.hasOwnProperty("phase_id") ? $routeParams.phase_id : null;

  self.newPhaseName = '';
  self.bpmnNames = [];
  self.selectedBpmnName = 'Select BPMN';

  self.projects = [];
  self.selectedProject = null;
  self.newProjectName = '';

  self.phases = [];
  self.selectedPhase = null;
  self.phaseManagers = [];
  self.selectedPhaseManager = null;

  self.bpmnTraversalData = '';

  $http.get('baf/PhaseBpmnNamesFetch').then(
    function(resp) {
      self.bpmnNames = resp.data;
      $log.log('OK GET baf/PhaseBpmnNamesFetch (' + self.bpmnNames.length + ') objects');
    },
    function() {
      $log.log('ERROR GET baf/PhaseBpmnNamesFetch');
    }
  )

  $http.get('api/Person').then(
    function(resp) {
      self.phaseManagers = resp.data.map(function(p){return {_id: p._id, name: p.first_name + ' ' + p.last_name};});
      $log.log('OK GET api/person (' + self.phaseManagers.length + ') objects');
    },
    function() {
      $log.log('ERROR GET api/Person');
    }
  )

  self.canCreateProject = function() {
    return AuthService.data.roles.join(',').indexOf('BW-Create-Project') != -1;
  }

  self.fetchProjects = function(projectIdToSelect, phaseIdToSelect) {
    self.selectedProject = null;
    self.selectedPhase = null;
    self.phases = [];
    query = 'baf/OwnedProjects?person_id=' + AuthService.data._id;
    $log.log('GET ' + query);
    self.busy = true;
    $http.get(query).then(
      function(resp) {
        self.busy = false;
        self.projects = resp.data;
        $log.log('OK GET ' + query + ' (' + self.projects.length + ') objects');
        if (projectIdToSelect) {
          self.selectProject(projectIdToSelect, phaseIdToSelect);
        }
      },
      function(errResponse) {
        self.busy = false;
        alert("ERROR(collection-details): " + errResponse);
      }
    );
  }

  self.selectProject = function(projectId, phaseId) {
    $log.log('Called selectProject(' + projectId + ', ' + phaseId + ')');
    if (projectId) {
      query = 'baf/OwnedPhases?person_id=' + AuthService.data._id + '&project_id=' + projectId;
      $log.log('GET ' + query);
      self.busy = true;
      $http.get(query).then(
        function(resp) {
          self.busy = false;
          self.phases = resp.data;
          $log.log('OK GET ' + query + ' (' + self.phases.length + ') objects');
          self.selectedProject = self.projects.filter(function(p){return p._id == projectId;})[0];
          self.selectedPhase = phaseId ? self.phases.filter(function(p){return p._id == phaseId;})[0] : null;
          if (phaseId) {
            self.selectedPhaseManager = self.phaseManagers.
              filter(function(pm){return pm._id == self.selectedPhase.admin_person_id})[0];
          }
        },
        function(errResponse) {
          self.busy = false;
          alert("ERROR GET " + query);
        }
      );
    } else {
      self.selectedProject = null;
      self.selectedPhase = null;
    }
  }

  self.createNewProject = function() {
    $log.log('Called createNewProject()');
    var postData = '{"name": "' + self.newProjectName + '", "status": "created", ' +
        '"admin_person_id": ObjectId("' + AuthService.data._id + '")}';
    self.busy = true;
    $http.post('api/Project', postData).then(
      function(resp) {
        self.busy = false;
        $log.log('OK POST api/Project');
        self.newProjectName = '';
        self.fetchProjects(resp.data._id);
      },
      function() {
        self.busy = false;
        $log.log('ERROR POST api/Project');
      }
    )
  }

  self.projectPublicChanged = function() {
    $log.log('Called projectPublicChanged()');
    var query = 'baf/ProjectSetPublic?project_id=' + self.selectedProject._id + '&public=' + self.selectedProject.public;
    self.busy = true;
    $http.post(query).then(
      function() {
        self.busy = false;
        $log.log('OK POST ' + query);
      },
      function() {
        self.busy = false;
        $log.log('ERROR POST ' + query);
      }
    )
  }

  self.launchSelectedProject = function() {
    var project = self.selectedProject;
    var query = 'baf/ProjectLaunch?project_id=' + project._id;
    $log.log('calling POST ' + query);
    self.busy = true;
    $http.post(query).then(
      function(resp) {
        self.busy = false;
        $log.log('OK POST ' + query);
        self.fetchProjects(project._id);
      },
      function(errResponse) {
        self.busy = false;
        $log.log('ERROR POST ' + query);
      }
    );
  }

  self.endSelectedProject = function() {
    var project = self.selectedProject;
    var query = 'baf/ProjectEnd?project_id=' + project._id;
    $log.log('calling POST ' + query)
    self.busy = true;
    $http.post(query).then(
      function(resp) {
        self.busy = false;
        $log.log('OK POST ' + query);
        self.fetchProjects(project._id);
      },
      function(errResponse) {
        self.busy = false;
        $log.log('ERROR POST ' + query);
      }
    );
  }

  self.deleteSelectedProject = function() {
    var query = 'api/Project/' + self.selectedProject._id;
    $log.log('calling DELETE ' + query)
    self.busy = true;
    $http.delete(query).then(
      function(resp) {
        self.busy = false;
        $log.log('OK DELETE ' + query);
        self.fetchProjects();
      },
      function(errResponse) {
        self.busy = false;
        $log.log('ERROR DELETE ' + query);
      }
    );
  }

  self.isProjectManager = function() {
    return self.selectedProject != null && AuthService.data._id == self.selectedProject.admin_person_id;
  }

  self.projectRowColor = function(project) {
    return project == self.selectedProject ? 'yellow' : 'white';
  }

  self.addPhaseDisabled = function() {
    return self.selectedProject.status == 'ended' || self.newPhaseName.trim() == '' ||
        self.selectedBpmnName == 'Select BPMN';
  }

  self.addPhase = function(name) {
    $log.log('Called addPhase()');
    var query = 'baf/PhaseAdd?bpmn_name=' + self.selectedBpmnName + '&project_id=' + self.selectedProject._id +
        '&admin_person_id=' + AuthService.data._id + '&phase_name=' + self.newPhaseName;
    self.busy = true;
    $http.post(query).then(
      function(resp) {
        self.busy = false;
        $log.log('OK POST ' + query);
        self.selectProject(self.selectedProject._id, resp.data._id);
        self.newPhaseName = '';
      },
      function() {
        self.busy = false;
        $log.log('ERROR POST ' + query);
      }
    )
  }

  self.selectedPhaseEnd = function(name) {
    var query = 'baf/PhaseEnd?phase_id=' + self.selectedPhase._id;
    self.busy = true;
    $http.post(query).then(
      function(resp) {
        self.busy = false;
        $log.log('OK POST ' + query);
      },
      function() {
        self.busy = false;
        $log.log('ERROR POST ' + query);
      }
    )
  }

  self.bpmnNameSet = function(name) {
    self.selectedBpmnName = name;
    $log.log('Called bpmnNameSet(' + name + ')');
  }

  self.selectPhase = function(phase) {
    $log.log('Called selectPhase(' + phase.name + ')');
    self.selectedPhase = phase;
    $log.log('phase.admin_person_id: ' + phase.admin_person_id);
    self.selectedPhaseManager = self.phaseManagers.filter(function(p){return p._id == phase.admin_person_id;})[0];
    $log.log('selectedPhaseManager: ' + JSON.stringify(self.selectedPhaseManager));
  }

  self.isPhaseManager = function() {
    return self.selectedPhase != null && AuthService.data._id == self.selectedPhase.admin_person_id;
  }

  self.phaseRowColor = function(phase) {
    return phase == self.selectedPhase ? 'yellow' : 'white';
  }

  self.phaseManagerSelect = function(pm) {
    self.selectedPhaseManager = pm;
    $log.log('Called phaseManagerSelect(' + pm.name + ')');
  }

  self.phaseManagerSet = function() {
    var query = 'baf/PhaseAdministratorSet?person_id=' + self.selectedPhaseManager._id +
        '&project_id=' + self.selectedProject._id + '&phase_id=' + self.selectedPhase._id;
    self.busy = true;
    $http.post(query).then(
      function(resp) {
        self.busy = false;
        self.selectProject(self.selectedProject._id, self.selectedPhase._id);
        self.selectedPhase.admin_person_id = self.selectedPhaseManager._id;
        $log.log('OK POST ' + query);
      },
      function(resp) {
        self.busy = false;
        $log.log('ERROR POST ' + query);
      }
    );
    $log.log('Called phaseManagerSet(' + self.selectedPhaseManager.name + ')');
  }

  self.selectedPhaseLaunch = function() {
    $log.log('Called selectedPhaseLaunch(' + self.selectedPhase.name + ')');
    var query = 'baf/PhaseLaunch?project_id=' + self.selectedProject._id + '&phase_id=' + self.selectedPhase._id +
        '&phase_bpmn_name=' + self.selectedPhase.bpmn_name;
    $log.log('calling POST ' + query)
    self.busy = true;
    $http.post(query).then(
      function(resp) {
        self.busy = false;
        $log.log('OK POST ' + query);
        self.selectProject(self.selectedProject._id, self.selectedPhase._id);
      },
      function(resp) {
        self.busy = false;
        $log.log('ERROR POST ' + query);
      }
    );
  }

  self.selectedPhaseDeletable = function() {
    var deletable = new RegExp('defined|ended')
    return deletable.test(self.selectedPhase.status);
  }

  self.selectedPhaseCanLaunch = function() {
    return self.selectedPhase.status == 'defined' && self.selectedProject.status == 'running';
  }

  self.selectedPhaseDelete = function() {
    $log.log('Called selectedPhaseDelete(' + self.selectedPhase.name + ')');
    var query = 'api/Phase/' + self.selectedPhase._id;
    $log.log('calling DELETE ' + query)
    self.busy = true;
    $http.delete(query).then(
      function(resp) {
        self.busy = false;
        $log.log('OK DELETE ' + query);
        self.selectProject(self.selectedProject._id);
      },
      function(resp) {
        self.busy = false;
        $log.log('ERROR DELETE ' + query);
      }
    );
  }

  self.refresh = function() {
    var href = $window.location.href;
    var dtIdx = href.indexOf('project_id=');
    if (dtIdx != -1) {
      href = href.substring(0, dtIdx - 1);
    } else {
      dtIdx = href.indexOf('dt=');
      if (dtIdx != -1) {
        href = href.substring(0, dtIdx - 1);
      }
    }
    if (self.selectedProject != null) {
      href += '?project_id=' + self.selectedProject._id;
      if (self.selectedPhase != null) {
        href += '&phase_id=' + self.selectedPhase._id;
      }
      href += '&dt=';
    } else {
      href += '?dt=';
    }
    href += escape(new Date().getTime());
    //$log.log('Refresh location: ' + href);
    $window.location.href = href;
  }

  self.statusColor = function(displayStatus) {
    var color = 'white';
    switch (displayStatus) {
	  case 'defined':
		color = 'yellow';
		break;
	  case 'waiting':
		color = 'Red';
		break;
	  case 'waiting2':
		color = 'Pink';
		break;
	  case 'started':
		color = 'Lime';
		break;
	  case 'running':
		color = 'Lime';
		break;
	  case 'idle':
		color = 'DarkGreen';
		break;
	  case 'ended':
	    color = 'Silver';
	    break;
	  default:
	    color = 'white';
	    break;
    }
    return color;
  }

  self.startUpload = function(bpmn_name) {
    $log.log('Called startUpload()');
    var uploadButton = $window.document.getElementById('configuration-upload-button');
    uploadButton.addEventListener('change', function(evt) {self.uploadBegin(evt, bpmn_name)}, false);
    uploadButton.click();
    $log.log('Exiting startUpload()');
  }

  self.uploadBegin = function(evt, bpmn_name) {
    $log.log('Called uploadBegin()');
    var uploadButton = $window.document.getElementById('configuration-upload-button');
    uploadButton.removeEventListener('change', self.uploadBegin, false);
    var files = evt.target.files; // FileList of File objects
    if (files.length > 0) {
      var formData = new FormData();
      angular.forEach(files, function(file, index) {
        formData.append(file.name, file, file.name);
        $log.log('formData.append(' + file.name + ')');
      });
      var query = 'baf/PhaseConfigurationUpload?project_id=' + self.selectedProject._id +
          '&phase_id=' + self.selectedPhase._id + '&bpmn_name=' + bpmn_name;
      $log.log("POST: " + query);
      self.busy = true;
      $http.post(query, formData, {transformRequest: angular.identity, headers: {'Content-Type': undefined}}).then(
        function(resp) {
          $log.log('OK-function: ' + query);
          self.busy = false;
          alert('OK: Uploading ' + files.length + ' files completed');
        },
        function(resp) {
          $log.log('ERROR-function: ' + query);
          self.busy = false;
          alert('ERROR: Uploading ' + files.length + ' files failed');
        }
      )
    }
    $log.log('Exiting uploadBegin()');
  }

  self.fetchProjects(self.initialProjectId, self.initialPhaseId);

}]);