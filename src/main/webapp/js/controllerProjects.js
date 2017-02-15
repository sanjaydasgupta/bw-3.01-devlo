angular.module('BuildWhizApp')

.controller("ProjectsCtrl", ['$log', '$http', 'AuthenticationService', function ($log, $http, AuthService) {

  var self = this;

  self.newPhaseNames = [];
  self.selectedPhaseName = 'Select Phase';

  self.projects = [];
  self.selectedProject = null;

  self.phases = [];
  self.selectedPhase = null;

  $http.get('baf/PhaseBpmnNamesFetch').then(
    function(resp) {
      self.newPhaseNames = resp.data;
      $log.log('OK GET baf/PhaseBpmnNamesFetch');
    },
    function() {
      $log.log('ERROR GET baf/PhaseBpmnNamesFetch');
    }
  )

  self.canCreateProject = function() {
    return AuthService.data.roles.join(',').indexOf('BW-Create-Project') != -1;
  }

  self.fetchProjects = function(projectIdToSelect) {
    self.selectedProject = null;
    self.selectedPhase = null;
    self.phases = [];
    query = 'baf/OwnedProjects?person_id=' + AuthService.data._id;
    $log.log('GET ' + query);
    $http.get(query).then(
      function(resp) {
        self.projects = resp.data;
        $log.log('OK GET ' + self.projects.length + ' objects');
        if (projectIdToSelect) {
          var p2 = self.projects.filter(function(p){return p._id == projectIdToSelect;});
          if (p2.length > 0) {
            self.selectProject(p2[0]);
          }
        }
      },
      function(errResponse) {alert("ERROR(collection-details): " + errResponse);}
    );
  }

  self.selectProject = function(project) {
    $log.log('Called selectProject(' + project.name + ')');
    if (project) {
      query = 'baf/OwnedPhases?person_id=' + AuthService.data._id + '&project_id=' + project._id;
      $log.log('GET ' + query);
      $http.get(query).then(
        function(resp) {
          self.phases = resp.data;
          $log.log('OK GET ' + self.phases.length + ' objects');
          self.selectedProject = project;
          self.selectedPhase = null;
        },
        function(errResponse) {alert("ERROR GET " + query);}
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
    $http.post('api/Project', postData).then(
      function() {
        $log.log('OK POST api/Project');
        self.newProjectName = '';
        self.fetchProjects();
      },
      function() {
        $log.log('ERROR POST api/Project');
      }
    )
  }

  self.projectPublicChanged = function() {
    $log.log('Called projectPublicChanged()');
    var query = 'baf/ProjectSetPublic?project_id=' + self.selectedProject._id + '&public=' + self.selectedProject.public;
    $http.post(query).then(
      function() {
        $log.log('OK POST ' + query);
      },
      function() {
        $log.log('ERROR POST ' + query);
      }
    )
  }

  self.launchSelectedProject = function() {
    var project = self.selectedProject;
    var query = 'baf/ProjectLaunch?project_id=' + project._id;
    $log.log('calling POST ' + query);
    $http.post(query).then(
      function(resp) {
        $log.log('OK POST ' + query);
        self.fetchProjects(project._id);
      },
      function(errResponse) {$log.log('ERROR POST ' + query);}
    );
  }

  self.endSelectedProject = function() {
    var project = self.selectedProject;
    var query = 'baf/ProjectEnd?project_id=' + project._id;
    $log.log('calling POST ' + query)
    $http.post(query).then(
      function(resp) {
        $log.log('OK POST ' + query);
        self.fetchProjects(project._id);
      },
      function(errResponse) {$log.log('ERROR POST ' + query);}
    );
  }

  self.deleteSelectedProject = function() {
    var query = 'api/Project/' + self.selectedProject._id;
    $log.log('calling DELETE ' + query)
    $http.delete(query).then(
      function(resp) {
        $log.log('OK DELETE ' + query);
        self.fetchProjects();
      },
      function(errResponse) {$log.log('ERROR DELETE ' + query);}
    );
  }

  self.canDisplayProject = function() {
    return self.selectedProject != null && AuthService.data._id == self.selectedProject.admin_person_id;
  }

  self.projectRowColor = function(project) {
    return project == self.selectedProject ? 'yellow' : 'white';
  }

  self.addPhase = function(name) {
    $log.log('Called addPhase()');
    var query = 'baf/PhaseAdd?phase_name=' + self.selectedPhaseName + '&project_id=' + self.selectedProject._id +
        '&admin_person_id=' + AuthService.data._id;
    $http.post(query).then(
      function() {
        self.selectProject(self.selectedProject);
        $log.log('OK POST ' + query);
      },
      function() {
        $log.log('ERROR POST ' + query);
      }
    )
  }

  self.newPhaseNameSet = function(name) {
    self.selectedPhaseName = name;
    $log.log('Called newPhaseNamesSet(' + name + ')');
  }

  self.selectPhase = function(phase) {
    $log.log('Called selectPhase(' + phase.name + ')');
    self.selectedPhase = phase;
  }

  self.canDisplayPhase = function() {
    return self.selectedPhase != null && AuthService.data._id == self.selectedPhase.admin_person_id;
  }

  self.phaseRowColor = function(phase) {
    return phase == self.selectedPhase ? 'yellow' : 'white';
  }

  self.fetchProjects();

}]);