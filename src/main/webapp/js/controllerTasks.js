angular.module('BuildWhizApp')

.controller("TasksCtrl", ['$log', '$http', '$window', 'AuthenticationService',
    function ($log, $http, $window, AuthService) {

  var self = this;

  self.taskList = [];
  self.selectedTask = null;
  self.taskSelected = false;
  self.confirmingCompletion = false;
  self.progressReportAttachments = [];
  self.fetchKey = 'All';

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
    var filterKey = filter ? filter : 'All';
    var query = 'baf/OwnedActionsSummary?person_id=' + AuthService.data._id + '&filter_key=' + filterKey.toLowerCase();
    $log.log('TasksCtrl: GET ' + query);
    $http.get(query).then(
      function(resp) {
        self.taskList = resp.data;
        self.fetchKey = filterKey;
        $log.log('OK-TasksCtrl: got ' + self.taskList.length + ' objects');
      },
      function(errResponse) {alert("TasksCtrl: ERROR(collection-details): " + errResponse);}
    );
  }

  self.submitProgressReport = function() {
    var formData = new FormData();
    angular.forEach(self.progressReportAttachments, function(file, index) {
      formData.append(file.name, file, file.name);
      $log.log('formData.append(' + file.name + ', ' + JSON.stringify(file) + ', file)');
    });
    var query = 'baf/ProgressReportSubmit?person_id=' + AuthService.data._id;
    $http.post(query, formData, {transformRequest: angular.identity, headers: {'Content-Type': undefined}}).then(
      function() {
        $log.log('OK submitProgressReport')
      },
      function() {
        $log.log('ERROR submitProgressReport')
      }
    )
  }

  self.handleFile = function(evt) {
    var files = evt.target.files; // FileList of File objects
    if (files.length > 0) {
      var file = files[0];
      var fileDescription = 'name: ' + file.name + ', type: ' + file.type + ', size: ' + file.size;
      $log.log('File: ' + fileDescription);
      self.progressReportAttachments.push(file);
    }
  }

  self.captureFile = function() {
      var fileButton = $window.document.getElementById('file-button');
      $log.log('BEFORE File-Button addEventListener: ' + fileButton);
      fileButton.addEventListener('change', self.handleFile, false);
      fileButton.click();
      $log.log('AFTER File-Button click: ' + fileButton);
  }

  self.handlePhoto = function(evt) {
    var files = evt.target.files; // FileList of File objects
    if (files.length > 0) {
      var photo = files[0];
      var photoDescription = 'name: ' + photo.name + ', type: ' + photo.type + ', size: ' + photo.size;
      $log.log('Photo: ' + photoDescription);
      self.progressReportAttachments.push(photo);
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