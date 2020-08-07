angular.module('BuildWhizApp')

.controller("StatusUpdateCtrl", ['$log', '$http', 'AuthenticationService', '$window',
    function ($log, $http, AuthService, $window) {

  var self = this;

  self.busy = false;

  self.updates = [];
  self.submissionAttachments = [];
  self.submissionMessage = '';
  self.submissionType = 'Progress';
  self.submissionTitle = '';
  self.showInfo = false;

  var q = 'baf/ProgressReportList?person_id=' + AuthService.data._id + '&timezone=' + AuthService.data.tz;
  $log.log('Calling GET ' + q);
  self.busy = true;
  $http.get(q).then(
    function(resp) {
      self.updates = resp.data;
      self.busy = false;
      $log.log('OK GET ' + q);
    },
    function(resp) {
      $log.log('ERROR GET ' + q);
    }
  )

  self.toggleInfoDisplay = function() {
    self.showInfo = !self.showInfo;
    $log.log('Calling toggleInfoDisplay: ' + self.showInfo);
  }

  self.submitProgressReport = function() {
    var data = {person_id: AuthService.data._id, title: self.submissionTitle, update_type: self.submissionType,
        message: self.submissionMessage};
    var formData = new FormData();
    var dataBlob = new Blob([JSON.stringify(data)], {type : 'application/json'});
    formData.append("form-data", dataBlob);
    angular.forEach(self.submissionAttachments, function(file, index) {
      formData.append(file.name, file, file.name);
      $log.log('formData.append(name: ' + file.name + ', stringified: ' + JSON.stringify(file) + ')');
    });
    var query = '/bw-3.01/baf/ProgressReportSubmit'
    $log.log('POST ' + query);
    self.busy = true;
    $http.post(query, formData, {transformRequest: angular.identity, headers: {'Content-Type': undefined}}).then(
      function() {
        self.submissionAttachments = [];
        self.submissionType = 'Progress';
        self.submissionTitle = '';
        self.submissionMessage = '';
        $log.log('OK submitProgressReport')
        self.busy = false;
      },
      function() {
        $log.log('ERROR submitProgressReport')
        self.busy = false;
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

  self.isAdmin = function() {
    return AuthService.data.roles.join(',').indexOf('BW-Admin') != -1;
  }

}]);