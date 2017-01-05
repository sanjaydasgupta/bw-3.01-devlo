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

  self.findModeActive = false;
  self.newModeActive = false;

  self.documentList = [];
  self.documentCount = 0;
  self.currentAuthor = {_id: '', name: 'Any'};
  self.currentContentKey = "Any";
  self.currentSubcategoryKey = "Any";
  self.currentCategoryKey = "Any";
  self.selectedDate = new Date();
  self.versionComments = '';
  self.documentName = '';
  self.documentDescription = '';

  self.records = [];
  self.recordSelected = false;
  self.selectedRecord = null;
  self.versions = [];

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

  self.uploadDisabled = function() {
    return self.currentAuthor._id == '' || self.versionComments == '';
  }

  self.upload = function() {
    $log.log('Called upload()');
    var uploadButton = $window.document.getElementById('document-upload-button');
    uploadButton.addEventListener('change', self.uploadBegin, false);
    uploadButton.click();
    $log.log('Exiting upload()');
  }

  self.uploadBegin = function(evt) {
    $log.log('Called uploadBegin()');
    var uploadButton = $window.document.getElementById('document-upload-button');
    uploadButton.removeEventListener('change', self.uploadBegin, false);
    var files = evt.target.files; // FileList of File objects
    if (files.length > 0) {
      var timestamp = self.selectedDate.getTime();
      var formData = new FormData();
      angular.forEach(files, function(file, index) {
        formData.append(file.name, file, file.name);
        $log.log('formData.append(name: ' + file.name);
      });
      var query = 'baf/DocumentPreload?person_id=' + AuthService.data._id +
          '&timestamp=' + timestamp + '&comments=' + self.versionComments +
          '&author_person_id=' + self.currentAuthor._id;
      if (self.recordSelected) {
        query += '&document_master_id=' + self.selectedRecord._id + '&category=' + escape(self.selectedRecord.category) +
            '&subcategory=' + escape(self.selectedRecord.subcategory) + '&content=' + self.selectedRecord.content +
            '&name=' + escape(self.selectedRecord.name) + '&description=' + escape(self.selectedRecord.description);
      } else {
        query += '&category=' + escape(self.currentCategoryKey) +
            '&subcategory=' + escape(self.currentSubcategoryKey) + '&content=' + self.currentContentKey +
            '&name=' + escape(self.documentName) + '&description=' + escape(self.documentDescription);
      }
      $log.log("POST: " + query);
      $http.post(query, formData, {transformRequest: angular.identity, headers: {'Content-Type': undefined}}).then(
        function() {
          $log.log('OK ' + query);
        },
        function() {
          $log.log('ERROR ' + query);
        }
      )
    }
    $log.log('Exiting uploadBegin()');
  }

//  self.resetFields = function() {
//    self.currentContentKey = 'Any';
//    self.currentCategoryKey = 'Any';
//    self.documentName = '';
//    self.documentDescription = '';
//    self.selectedRecord = null;
//    self.recordSelected = false;
//    self.records = [];
//  }
//
//  self.copyFields = function() {
//    self.currentContentKey = self.selectedRecord.content ? self.selectedRecord.content : 'Any';
//    self.currentSubcategoryKey = self.selectedRecord.subcategory ? self.selectedRecord.subcategory : 'Any';
//    self.currentCategoryKey = self.selectedRecord.category ? self.selectedRecord.category : 'Any';
//    self.documentName = self.selectedRecord.name;
//    self.documentDescription = self.selectedRecord.description;
//  }
//
  self.findRecords = function() {
    self.findModeActive = true;
    self.newModeActive = false;
    var q = 'baf/DocumentRecordFind?category=' + self.currentCategoryKey +
        '&subcategory=' + self.currentSubcategoryKey + '&content=' + self.currentContentKey +
        '&name=' + self.documentName + '&description=' + self.documentDescription;
    $log.log('GET ' + q);
    $http.get(q).then(
      function(resp) {
        self.records = resp.data;
        $log.log('OK GET ' + q + ' (' + self.records.length + ')')
        self.selectedRecord = null;
        self.recordSelected = false;
        //self.records.forEach(function(r) {r.selected = false;})
        self.versions = [];
      },
      function(resp) {
        $log.log('ERROR GET ' + q)
      }
    )
  }

  self.createRecordDisabled = function() {
    return self.currentCategoryKey == 'Any' || self.currentContentKey == 'Any' || self.documentName == '' ||
        self.documentDescription == '';
  }

  self.newRecord = function() {
    self.findModeActive = false;
    self.newModeActive = true;
    self.records = [];
    self.recordSelected = false;
    self.selectedRecord = null;
    self.versions = [];
//    var q = 'baf/DocumentRecordCreate?category=' + escape(self.currentCategoryKey) +
//        '&subcategory=' + escape(self.currentSubcategoryKey) +
//        '&content=' + self.currentContentKey + '&name=' + escape(self.documentName) +
//        '&description=' + escape(self.documentDescription);
//    $log.log('POST ' + q);
//    $http.post(q).then(
//      function(resp) {
//        $log.log('OK POST' + q)
//      },
//      function(resp) {
//        $log.log('ERROR POST' + q)
//      }
//    )
  }

  self.selectRecord = function(record) {
    self.selectedRecord = record;
    self.recordSelected = true;

    var q = 'baf/DocumentVersions?document_master_id=' + self.selectedRecord._id;
    $log.log('GET ' + q);
    $http.get(q).then(
      function(resp) {
        self.versions = resp.data;
        $log.log('OK GET ' + q + ' (' + self.versions.length + ')')
      },
      function(resp) {
        $log.log('ERROR GET ' + q)
      }
    )
  }

  self.getColor = function(record) {
    return (record == self.selectedRecord) ? 'yellow' : 'white';
  }

//  self.showUploadPanel = function() {
//    return self.recordSelected && AuthService.data.roles.join().indexOf('BW-Admin') != -1;
//  }
//
  self.isAdmin = function() {
    return AuthService.data.roles.join().indexOf('BW-Admin') != -1;
  }

}]);