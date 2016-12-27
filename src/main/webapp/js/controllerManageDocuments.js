﻿angular.module('BuildWhizApp')

.controller("ManageDocumentsCtrl", ['$log', '$http', 'AuthenticationService', '$window',
      function ($log, $http, AuthService, $window) {

  var self = this;

  self.documentCategories = ['Architecture', 'Civil', 'Electrical Design', 'Interior Design', 'Landscape Design',
      'Mechanical Design', 'Plumbing Design', 'Structure'];
  self.documentSubcategories = [];
  self.authorKeys = ['Owner', 'Manager', 'Supervisor', 'Collaborator'];

  self.documentList = [];
  self.documentCount = 0;
  self.currentFilterKey = 'All';
  self.currentAuthorKey = "Any";
  self.currentContentKey = "Any";
  self.currentSubcategoryKey = "Any";
  self.currentCategoryKey = "Any";
  self.selectedDate = new Date();

  self.today = function() {
    return new Date().toLocaleDateString();
  }

  self.dateOptions = {
    dateDisabled: false,
    formatYear: 'yy',
    maxDate: new Date(2018, 11, 31),
    minDate: new Date(2010, 0, 1),
    startingDay: 1
  };

  self.fetchDocuments = function(filter) {
    self.currentFilterKey = filter ? filter : 'All';
    var filterKey = filter ? filter : 'all';
    var query = 'baf/OwnedDocumentsSummary?person_id=' + AuthService.data._id + '&filter_key=' + filter;
    $log.log('HomeCtrl: GET ' + query);
    $http.get(query).then(
      function(resp) {
        self.documentList = resp.data;
        $log.log('OK-HomeCtrl: got ' + self.documentList.length + ' objects');
      },
      function(errResponse) {alert("HomeCtrl: ERROR(collection-details): " + errResponse);}
    );
  }

  self.fetchDocumentsByCategory = function(categoryKey) {
    $log.log('Setting category=' + categoryKey);
    self.currentCategoryKey = categoryKey;
  }

  self.fetchDocumentsBySubcategory = function(subcategoryKey) {
    $log.log('Setting subcategory=' + subcategoryKey);
    self.currentSubcategoryKey = subcategoryKey;
  }

  self.fetchDocumentsByContent = function(contentKey) {
    $log.log('Setting content=' + contentKey);
    self.currentContentKey = contentKey;
  }

  self.fetchDocumentsByAuthor = function(authorKey) {
    $log.log('Setting author=' + authorKey);
    self.currentAuthorKey = authorKey;
  }

  self.upload = function() {
    $log.log('Called upload()');
    var uploadButton = $window.document.getElementById('document-upload-button');
    uploadButton.addEventListener('change', self.uploadOk, false);
    uploadButton.click();
    $log.log('Exiting upload()');
  }

  self.uploadOk = function(evt) {
    $log.log('Called uploadOk()');
    var uploadButton = $window.document.getElementById('document-upload-button');
    uploadButton.removeEventListener('change', self.uploadOk, false);
    var files = evt.target.files; // FileList of File objects
    if (files.length > 0) {
      var year = self.selectedDate.getUTCFullYear();
      var month = self.selectedDate.getUTCMonth();
      var date = self.selectedDate.getUTCDate();
      var hours = self.selectedDate.getUTCHours();
      var minutes = self.selectedDate.getUTCMinutes();
      var seconds = self.selectedDate.getUTCSeconds();
      var formData = new FormData();
      angular.forEach(files, function(file, index) {
        formData.append(file.name, file, file.name);
        $log.log('formData.append(name: ' + file.name);
      });
      formData.append('end-marker', 'end-marker-content');
      var query = 'baf/DocumentUpload?person_id=' + AuthService.data._id +
          '&year=' + year + '&month=' + month + '&date=' + date +
          '&hours=' + hours + '&minutes=' + minutes + '&seconds=' + seconds +
          '&category=' + self.currentCategoryKey + '&subcategory=' + self.currentSubcategoryKey;
      $log.log("POST: " + query);
      $http.post(query, formData, {transformRequest: angular.identity, headers: {'Content-Type': undefined}}).then(
        function() {
          $log.log('OK submit FilesPreload');
        },
        function() {
          $log.log('ERROR submit FilesPreload');
        }
      )
    }
    $log.log('Exiting uploadOk()');
  }

  self.listFiles = function() {
    $log.log('Called listFiles()');
  }

}]);