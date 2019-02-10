angular.module('BuildWhizApp')

.controller("TasksCtrl", ['$log', '$http', '$window', 'AuthenticationService',
    function ($log, $http, $window, AuthService) {

  var self = this;

  self.taskList = [];
  self.selectedTask = null;
  self.confirmingCompletion = false;
  self.submissionAttachments = [];
  self.currentFilterKey = 'All';
  self.userNameEmail = 'abc@buildwhiz.com';
  self.submissionType = 'Progress';
  self.submissionTitle = '';
  self.submissionMessage = '';

  self.selectTask = function(task) {
    if (task) {
      self.selectedTask = task;
      var message = 'TasksCtrl: selected ' + task.name + ' (activity: ' + task.activity_id + ') selected';
      $log.log(message)
    } else {
      self.selectedTask = null;
    }
  }

  self.fetchActions = function(filter) {
    $log.log('Called fetchActions()');
    var filterKey = filter ? filter : 'All';
    var query = 'baf2/ActionList?filter_key=' + filterKey.toLowerCase();
    $log.log('Calling GET ' + query);
    $http.get(query).then(
      function(resp) {
        self.taskList = resp.data;
        self.currentFilterKey = filterKey;
        $log.log('OK ' + query + ' (' + self.taskList.length + ' objects)');
        self.selectTask(); // select none
      },
      function(errResponse) {
        $log.log('ERROR ' + query);
        alert('ERROR ' + query);
      }
    );
  }

  self.submitProgressReport = function() {
    var formData = new FormData();
    angular.forEach(self.submissionAttachments, function(file, index) {
      formData.append(file.name, file, file.name);
      $log.log('formData.append(name: ' + file.name + ', stringified: ' + JSON.stringify(file) + ')');
    });
    formData.append('end-marker', 'end-marker-content');
    var query = 'baf/ProgressReportSubmit?person_id=' + AuthService.data._id +
    '&activity_id=' + self.selectedTask.activity_id + '&action_name=' + self.selectedTask.name +
    '&submission_title=' + self.submissionTitle + '&submission_type=' + self.submissionType +
    '&submission_message=' + self.submissionMessage;
    $http.post(query, formData, {transformRequest: angular.identity, headers: {'Content-Type': undefined}}).then(
      function() {
        self.submissionAttachments = [];
        self.submissionType = 'Progress';
        self.submissionTitle = '';
        self.submissionMessage = '';
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
      var temp = self.submissionAttachments.slice();
      temp.push(file);
      self.submissionAttachments = temp;
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
      var temp = self.submissionAttachments.slice();
      temp.push(photo);
      self.submissionAttachments = temp;
    }
  }

  self.takePhoto = function() {
    var cameraButton = $window.document.getElementById('camera-button');
    $log.log('BEFORE Camera-Button addEventListener: ' + cameraButton);
    cameraButton.addEventListener('change', self.handlePhoto, false);
    cameraButton.click();
    $log.log('AFTER Camera-Button click: ' + cameraButton);
  }

  self.actionComplete = function() {
    $log.log('TasksCtrl: taskComplete() Called');
    var query = 'baf2/ActionComplete?activity_id=' + self.selectedTask.activity_id +
        '&action_name=' + self.selectedTask.name +
        '&completion_message=' + self.selectedTask.completion_message +
        '&review_ok=' + (self.selectedTask.reviewOk ? 'OK' : 'Not-Ok');
    $log.log('TasksCtrl: taskComplete() POST ' + query);
    $http.post(query).then(
      function(resp) {
        self.confirmingCompletion = false;
        self.fetchActions(self.currentFilterKey);
        $log.log('TasksCtrl: taskComplete(): OK');
      },
      function(errResponse) {alert("TasksCtrl: ERROR(taskComplete()): " + errResponse);}
    );
  }

  self.displayName = function(task) {
//    if (task.type == 'main') {
//      return task.name;
//    } else {
//      return task.name + ':' + task.activity_name;
//    }
    return task.project_name + '/' + task.phase_name + '/' + task.name;
  }

  self.fetchActions();

}]);