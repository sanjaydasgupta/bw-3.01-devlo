angular.module('BuildWhizApp')

.controller("HomeCtrl", ['$log', '$http', 'AuthenticationService', function ($log, $http, AuthService) {

  var self = this;

//  self.taskList = [];
  self.projectList = [];
//  self.documentList = [];
  self.rfiStatus = '';
  self.tasksStatus = '';

  self.fetchRfiStatus = function() {
    var query = 'baf/RFIFetchStatus?person_id=' + AuthService.data._id;
    $log.log('HomeCtrl: GET ' + query);
    $http.get(query).then(
      function(resp) {
        var status = resp.data;
        self.rfiStatus = status.unread + ' / ' + status.total;
        $log.log('OK-HomeCtrl: got ' + JSON.stringify(status));
      },
      function(errResponse) {alert("HomeCtrl: ERROR(collection-details): " + errResponse);}
    );
  }

  self.fetchTasksStatus = function() {
    var query = 'baf/OwnedTasksStatusSummary?person_id=' + AuthService.data._id;
    $log.log('HomeCtrl: GET ' + query);
    $http.get(query).then(
      function(resp) {
        var status = resp.data;
        self.tasksStatus =
            (status.hasOwnProperty('waiting') ? status.waiting : 0) + '/' +
            (status.hasOwnProperty('defined') ? status.defined : 0) + '/' +
            (status.hasOwnProperty('ended') ? status.ended : 0);
        $log.log('OK-HomeCtrl: got ' + JSON.stringify(status));
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

  self.refresh = function() {
    self.fullName = AuthService.data.first_name + ' ' + AuthService.data.last_name;
    self.fetchRfiStatus();
    self.fetchProjects();
    self.fetchTasksStatus();
  }

  self.uiHidden = function() {
    $log.log('Called uiHidden()');
    var exists = AuthService.data.hasOwnProperty("ui_hidden");
    var hidden =  exists && AuthService.data.ui_hidden == true;
    $log.log('uiHidden() Exists -> ' + exists);
    $log.log('uiHidden() -> ' + hidden);
    return hidden;
  }

  self.refresh();

}]);