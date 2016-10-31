angular.module('BuildWhizApp')

.controller("ProjectsCtrl", ['$log', '$http', 'AuthenticationService', function ($log, $http, AuthService) {

  var self = this;

  self.projectList = [];

  self.fetchProjects = function() {
    query = 'person_id=' + AuthService.data._id;
    $log.log('ProjectsCtrl: GET baf/OwnedProjects?' + query);
    $http.get('baf/OwnedProjects?' + query).then(
      function(resp) {
        self.projectList = resp.data;
        $log.log('OK-ProjectsCtrl: got ' + self.projectList.length + ' objects');
      },
      function(errResponse) {alert("ProjectsCtrl: ERROR(collection-details): " + errResponse);}
    );
  }

  self.fetchProjects();

}]);