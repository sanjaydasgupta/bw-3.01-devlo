angular.module('BuildWhizApp')

.controller("ProjectsCtrl", ['$log', '$http', 'AuthenticationService', function ($log, $http, AuthService) {

  var self = this;

  self.newPhaseNames = [];
  self.selectedPhaseName = 'Select Phase';

  self.projects = [];
  self.selectedProject = null;

  self.phases = [];
  self.selectedPhase = null;

  self.selectedDetail = '';

  $http.get('baf/PhaseBpmnNamesFetch').then(
    function(resp) {
      self.newPhaseNames = resp.data;
      $log.log('OK GET baf/PhaseBpmnNamesFetch');
    },
    function() {
      $log.log('ERROR GET baf/PhaseBpmnNamesFetch');
    }
  )

  self.fetchProjects = function() {
    query = 'baf/OwnedProjects?person_id=' + AuthService.data._id;
    $log.log('GET ' + query);
    $http.get(query).then(
      function(resp) {
        self.projects = resp.data;
        $log.log('OK GET ' + self.projects.length + ' objects');
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
          self.selectedDetail = 'project';
          self.selectedPhase = null;
        },
        function(errResponse) {alert("ERROR GET " + query);}
      );
    } else {
      self.selectedProject = null;
      self.selectedPhase = null;
    }
  }

  self.canDisplayProject = function() {
    return self.selectedDetail == 'project' && AuthService.data._id == self.selectedProject.admin_person_id;
  }

  self.projectRowColor = function(project) {
    return project == self.selectedProject ? 'yellow' : 'white';
  }

  self.newPhaseNameSet = function(name) {
    self.selectedPhaseName = name;
    $log.log('Called newPhaseNamesSet(' + name + ')');
  }

  self.selectPhase = function(phase) {
    $log.log('Called selectPhase(' + phase.name + ')');
    self.selectedPhase = phase;
    self.selectedDetail = 'phase';
  }

  self.canDisplayPhase = function() {
    return self.selectedDetail == 'phase' && AuthService.data._id == self.selectedPhase.admin_person_id;
  }

  self.phaseRowColor = function(phase) {
    return phase == self.selectedPhase ? 'yellow' : 'white';
  }

  self.fetchProjects();

}]);