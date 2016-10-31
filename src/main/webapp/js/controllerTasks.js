angular.module('BuildWhizApp')

.controller("TasksCtrl", ['$log', '$http', 'AuthenticationService', function ($log, $http, AuthService) {

  var self = this;

  self.taskList = [];

  self.fetchActions = function() {
    query = 'person_id=' + AuthService.data._id;
    $log.log('HomeCtrl: GET baf/OwnedActionsAll?' + query);
    $http.get('baf/OwnedActionsAll?' + query).then(
      function(resp) {
        self.taskList = resp.data;
        $log.log('OK-HomeCtrl: got ' + self.taskList.length + ' objects');
      },
      function(errResponse) {alert("HomeCtrl: ERROR(collection-details): " + errResponse);}
    );
  }

  self.fetchActions();

}]);