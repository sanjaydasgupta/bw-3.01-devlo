angular.module('BuildWhizApp')

.controller("ProjectsCtrl", ['$log', '$http', 'AuthenticationService', function ($log, $http, AuthService) {

  var self = this;

  self.projectList = [];
  self.projectSelected = false;
  self.selectedProject = null;

  self.select = function(project) {
    if (project) {
      self.selectedProject = project;
      self.projectSelected = true;
      var message = 'Project ' + project.name + ' selected';
      $log.log(message)
    } else {
      self.selectedProject = null;
      self.projectSelected = false;
    }
  }

  self.fetchProjects = function() {
    query = 'baf/OwnedProjects?person_id=' + AuthService.data._id;
    $log.log('ProjectsCtrl: GET ' + query);
    $http.get(query).then(
      function(resp) {
        self.projectList = resp.data;
        $log.log('OK-ProjectsCtrl: got ' + self.projectList.length + ' objects');
      },
      function(errResponse) {alert("ProjectsCtrl: ERROR(collection-details): " + errResponse);}
    );
  }

  self.fetchProjects();

}]);