angular.module('BuildWhizApp')

.controller("TasksCtrl", ['$log', '$http', 'AuthenticationService', function ($log, $http, AuthService) {

  var self = this;

  self.taskList = [];
  self.selectedTask = null;
  self.taskSelected = false;
  self.confirmingCompletion = false;

  self.select = function(task) {
    if (task) {
      self.selectedTask = task;
      self.taskSelected = true;
      var message = 'TasksCtrl: selected ' + task.name + ' (activity: ' + task.activity_id + ') selected';
      $log.log(message)
    } else {
      self.selectedTask = null;
      self.taskSelected = false;
    }
  }

  self.fetchActions = function(filter) {
    var filterKey = filter ? filter : 'all';
    var query = 'baf/OwnedActionsSummary?person_id=' + AuthService.data._id + '&filter_key=' + filter;
    $log.log('TasksCtrl: GET ' + query);
    $http.get(query).then(
      function(resp) {
        self.taskList = resp.data;
        $log.log('OK-TasksCtrl: got ' + self.taskList.length + ' objects');
      },
      function(errResponse) {alert("TasksCtrl: ERROR(collection-details): " + errResponse);}
    );
  }

  self.fetchActions();

}]);