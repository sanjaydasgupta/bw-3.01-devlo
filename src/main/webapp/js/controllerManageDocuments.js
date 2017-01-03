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
      var timestamp = self.selectedDate.getTime();
      var formData = new FormData();
      angular.forEach(files, function(file, index) {
        formData.append(file.name, file, file.name);
        $log.log('formData.append(name: ' + file.name);
      });
      var query = 'baf/DocumentPreload?person_id=' + AuthService.data._id +
          '&timestamp=' + timestamp + '&document_master_id=' + self.selectedRecord._id +
          '&comments=' + self.versionComments + '&author_person_id=' + self.currentAuthor._id;
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

  self.resetFields = function() {
    self.currentContentKey = 'Any';
    self.currentCategoryKey = 'Any';
    self.documentName = '';
    self.documentDescription = '';
    self.selectedRecord = null;
    self.recordSelected = false;
    self.records = [];
  }

  self.copyFields = function() {
    self.currentContentKey = self.selectedRecord.content ? self.selectedRecord.content : 'Any';
    self.currentSubcategoryKey = self.selectedRecord.subcategory ? self.selectedRecord.subcategory : 'Any';
    self.currentCategoryKey = self.selectedRecord.category ? self.selectedRecord.category : 'Any';
    self.documentName = self.selectedRecord.name;
    self.documentDescription = self.selectedRecord.description;
  }

  self.listRecords = function() {
    var q = 'baf/DocumentRecordFind?category=' + self.currentCategoryKey + '&subcategory=' + self.currentSubcategoryKey +
        '&content=' + self.currentContentKey + '&name=' + self.documentName + '&description=' + self.documentDescription;
    $log.log('GET ' + q);
    $http.get(q).then(
      function(resp) {
        self.records = resp.data;
        $log.log('OK GET ' + q + ' (' + self.records.length + ')')
        self.selectedRecord = null;
        self.recordSelected = false;
        self.records.forEach(function(r) {r.selected = false;})
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

  self.createRecord = function() {
    var q = 'baf/DocumentRecordCreate?category=' + escape(self.currentCategoryKey) +
        '&subcategory=' + escape(self.currentSubcategoryKey) +
        '&content=' + self.currentContentKey + '&name=' + escape(self.documentName) +
        '&description=' + escape(self.documentDescription);
    $log.log('POST ' + q);
    $http.post(q).then(
      function(resp) {
        $log.log('OK POST' + q)
      },
      function(resp) {
        $log.log('ERROR POST' + q)
      }
    )
  }

  self.selectRecord = function(record) {
    self.selectedRecord = record;
    self.recordSelected = true;
  }

  self.getColor = function(record) {
    return (record == self.selectedRecord) ? 'yellow' : 'white';
  }

}]);