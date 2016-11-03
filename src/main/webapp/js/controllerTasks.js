angular.module('BuildWhizApp')

.controller("TasksCtrl", ['$log', '$http', 'AuthenticationService', function ($log, $http, AuthService) {

  var self = this;

  self.taskList = [];
  self.selectedTask = null;
  self.taskSelected = false;

  self.select = function(task) {
    if (task) {
      self.selectedTask = task;
      self.taskSelected = true;
      var message = 'Task ' + task.name + ' (activity: ' + task.activity_id + ') selected'
      $log.log(message)
    } else {
      self.selectedTask = null;
      self.taskSelected = false;
    }
    //return void(0);
  }

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