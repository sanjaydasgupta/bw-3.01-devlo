angular.module('BuildWhizApp')

.controller("ManageDocumentsCtrl", ['$log', '$http', 'AuthenticationService', '$window',
      function ($log, $http, AuthService, $window) {

  var self = this;

  self.documentCategories = ["ArchiCAD", "Architecture", "Building Science", "Civil", "Contracts",
      "Electrical", "Elevator", "GeoTech Fld Rpts", "Interior", "Material Specs", "Mechanical", "Permits",
      "Plumbing", "Reports", "Revit", "Special Insp Rpts", "Structure"];

  self.documentSubcategories = [];
  self.authorKeys = ['Owner', 'Manager', 'Supervisor', 'Collaborator'];
  self.authors = [];
  self.filteredAuthors = [];
  self.authorsMask = '';

  self.documentList = [];
  self.documentCount = 0;
  self.currentFilterKey = 'All';
  self.currentAuthor = {_id: '', name: 'Any'};
  self.currentContentKey = "Any";
  self.currentSubcategoryKey = "Any";
  self.currentCategoryKey = "Any";
  self.selectedDate = new Date();
  self.versionComments = '';

  $http.get('api/Person').then(
    function(resp) {
      self.authors = resp.data.map(function(p) {
        var newPerson = {_id: p._id, name: p.first_name + ' ' + p.last_name};
        return newPerson;
      });
      self.filteredAuthors = self.authors;
      $log.log('OK GET api/Person (' + self.authors.length + ')');
    },
    function(resp) {
      $log.log('ERROR GET api/Person')
    }
  )

  self.today = function() {
    return new Date().toLocaleDateString();
  }

  self.setMidnight = function() {
    var d = new Date(self.selectedDate.getTime());
    d.setHours(0);
    d.setMinutes(0);
    d.setSeconds(0);
    self.selectedDate = d;
  }

  self.dateOptions = {
    dateDisabled: false,
    formatYear: 'yy',
    maxDate: new Date(2018, 11, 31),
    minDate: new Date(2010, 0, 1),
    startingDay: 1
  };

  self.filterAuthorNames = function() {
    $log.log('Called filterAuthorNames(' + self.authorsFilter + ')');
    if (self.authorsFilter = '') {
      self.filteredAuthors = self.authors;
    } else {
      self.filteredAuthors = self.authors.filter(function(a) {
        var name = a.name.toUpperCase();
        var mask = self.authorsMask.toUpperCase();
        var index = name.indexOf(mask);
        return name.indexOf(mask) != -1;
      });
    }
  }

  self.fetchDocumentsByCategory = function(categoryKey) {
    $log.log('Setting category=' + categoryKey);
    self.currentCategoryKey = categoryKey;
  }

  self.fetchDocumentsBySubcategory = function(subcategoryKey) {
    $log.log('Setting subcategory=' + subcategoryKey);
    self.currentSubcategoryKey = subcategoryKey;
  }

  self.fetchDocumentsByAuthor = function(author) {
    $log.log('Setting author=' + author);
    self.currentAuthor = author;
  }

  self.fetchDocumentsByContent = function(contentKey) {
    $log.log('Setting content=' + contentKey);
    self.currentContentKey = contentKey;
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

  self.createRecord = function() {
    $log.log('Called createRecord()');
  }

}]);