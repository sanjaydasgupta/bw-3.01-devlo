angular.module('BuildWhizApp')

.controller("TasksCtrl", ['$log', '$http', '$window', 'AuthenticationService',
    function ($log, $http, $window, AuthService) {

  var self = this;

  self.taskList = [];
  self.selectedTask = null;
  self.taskSelected = false;
  self.confirmingCompletion = false;
  self.photoDescription = '';

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
    var query = 'baf/OwnedActionsSummary?person_id=' + AuthService.data._id + '&filter_key=' + filterKey;
    $log.log('TasksCtrl: GET ' + query);
    $http.get(query).then(
      function(resp) {
        self.taskList = resp.data;
        $log.log('OK-TasksCtrl: got ' + self.taskList.length + ' objects');
      },
      function(errResponse) {alert("TasksCtrl: ERROR(collection-details): " + errResponse);}
    );
  }

  self.handlePhoto = function(evt) {
    var files = evt.target.files; // FileList of File objects
    if (files.length > 0) {
      var photo = files[0];
      self.photoDescription = 'name: ' + photo.name + ', type: ' + photo.type + ', size: ' + photo.size;
      $log.log('Photo ' + self.photoDescription);
      //var output = [];
      //for (var i = 0, f; f = files[i]; i++) {
      //  output.push('<li><strong>', escape(f.name), '</strong> (', f.type || 'n/a', ') - ', f.size,
      //      ' bytes, last modified: ', f.lastModifiedDate ? f.lastModifiedDate.toLocaleDateString() : 'n/a', '</li>');
      //}
      //document.getElementById('list').innerHTML = '<ul>' + output.join('') + '</ul>';
    }
  }

  self.takePhoto = function() {
      var cameraButton = $window.document.getElementById('camera-button');
      $log.log('BEFORE Camera-Button addEventListener: ' + cameraButton);
      cameraButton.addEventListener('change', self.handlePhoto, false);
      cameraButton.click();
      $log.log('AFTER Camera-Button click: ' + cameraButton);
  }

  self.fetchActions();

}]);