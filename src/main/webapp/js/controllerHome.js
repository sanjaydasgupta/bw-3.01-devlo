angular.module('BuildWhizApp')

.controller("HomeCtrl", ['$log', '$http', 'AuthenticationService', function ($log, $http, AuthService) {

  var self = this;

  self.taskList = [];
  self.projectList = [];

  self.fetchActions = function() {
    query = 'baf/OwnedActionsSummary?person_id=' + AuthService.data._id;
    $log.log('HomeCtrl: GET ' + query);
    $http.get(query).then(
      function(resp) {
        self.taskList = resp.data;
        $log.log('OK-HomeCtrl: got ' + self.taskList.length + ' objects');
      },
      function(errResponse) {alert("HomeCtrl: ERROR(collection-details): " + errResponse);}
    );
  }

  self.fetchProjects = function() {
    query = 'person_id=' + AuthService.data._id;
    $log.log('HomeCtrl: GET baf/OwnedProjects?' + query);
    $http.get('baf/OwnedProjects?' + query).then(
      function(resp) {
        self.projectList = resp.data;
        $log.log('OK-HomeCtrl-fetchProjects: got ' + self.projectList.length + ' objects');
      },
      function(errResponse) {alert("HomeCtrl: ERROR(collection-details): " + errResponse);}
    );
  }

  self.logout = function() {
    AuthService.logout();
  }

  self.fullName = AuthService.data.first_name + ' ' + AuthService.data.last_name;

  self.fetchProjects();
  self.fetchActions();

}]);