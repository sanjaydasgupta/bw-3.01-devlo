angular.module('BuildWhizApp')

.controller("LoaderDocumentsCtrl", ['$log', '$http', 'AuthenticationService', '$window',
      function ($log, $http, AuthService, $window) {

  var self = this;

  self.documentCategories = ["ArchiCAD", "Architecture", "Building Science", "Civil", "Contracts",
      "Electrical", "Elevator", "GeoTech Fld Rpts", "Interior", "Material Specs", "Mechanical", "Permits",
      "Plumbing", "Reports", "Revit", "Special Insp Rpts", "Structure"];

  self.documentSubcategories = [];

  self.contentTypes = [];
  self.authors = [];
  self.filteredAuthors = [];
  self.currentAuthor = {name: 'Select'};

  self.findModeActive = false;
  self.newModeActive = false;

  self.currentCategoryKey = 'Select';
  self.currentSubcategoryKey = 'Other';
  self.subcategoryText = '';
  self.documentName = '';
  self.documentDescription = '';
  self.versionComments = '';
  self.selectedDate = new Date();

  self.documents = [];
  self.selectedDocument = null;

  self.dateOptions = {
    dateDisabled: false,
    formatYear: 'yy',
    maxDate: new Date(2018, 11, 31),
    minDate: new Date(2010, 0, 1),
    startingDay: 1
  };


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

  $http.get('api/ContentType').then(
    function(resp) {
      self.contentTypes = resp.data.map(function(p) {
        var contentType = p.type;
        return contentType;
      });
      $log.log('OK GET api/ContentType (' + self.contentTypes + ')');
    },
    function(resp) {
      $log.log('ERROR GET api/contentType')
    }
  )

  self.fetchDocumentsByCategory = function(categoryKey) {
    var query = 'baf/DocumentSubcategoriesFetch?category=' + categoryKey;
    $log.log('calling GET ' + query);
    $http.get(query).then(
      function(resp) {
        self.documentSubcategories = resp.data;
        self.currentSubcategoryKey = 'Other';
        self.selectedDocument = null;
        self.documents = [];
        self.rfiList = [];
        self.selectedRfi = null;
        $log.log('OK GET ' + query + ' (' + resp.data.length + ')');
      },
      function(resp) {
        $log.log('ERROR GET ' + query);
      }
    );
    self.currentCategoryKey = categoryKey;
  }

  self.fetchDocumentsBySubcategory = function(subcategoryKey) {
    $log.log('Setting subcategory=' + subcategoryKey);
    self.currentSubcategoryKey = subcategoryKey;
    self.selectedDocument = null;
    self.documents = [];
    self.rfiList = [];
    self.selectedRfi = null;
  }

  self.setMidnight = function() {
    var d = new Date(self.selectedDate.getTime());
    d.setHours(0);
    d.setMinutes(0);
    d.setSeconds(0);
    self.selectedDate = d;
  }

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

  self.fetchDocumentsByAuthor = function(author) {
    self.currentAuthor = author;
    $log.log('Called fetchDocumentsByAuthor(' + author.name + ')')
  }

  self.uploadDisabled = function() {
    return self.currentAuthor.name == 'Select' || self.versionComments == '' || self.currentCategoryKey == 'Select' ||
        (self.currentSubcategoryKey == 'Other' && self.subcategoryText == '') || self.documentName == '' ||
        self.documentDescription == '';
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
        $log.log('formData.append(' + file.name + ')');
      });
      var query = 'baf/DocumentPreload?person_id=' + AuthService.data._id +
          '&timestamp=' + timestamp + '&comments=' + escape(self.versionComments) +
          '&author_person_id=' + self.currentAuthor._id + '&category=' + escape(self.currentCategoryKey) +
          '&subcategory=' + escape(self.currentSubcategoryKey == 'Other' ? self.subcategoryText : self.currentSubcategoryKey) +
          '&name=' + escape(self.documentName) + '&description=' + escape(self.documentDescription);
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

  self.findDisabled = function() {
    return self.currentCategoryKey == 'Select';
  }

  self.findDocuments = function() {
    var q = 'baf/DocumentSearch?category=' + escape(self.currentCategoryKey) +
        '&subcategory=' + escape(self.currentSubcategoryKey == 'Other' ? self.subcategoryText : self.currentSubcategoryKey) +
        '&content=Any&name=' + escape(self.documentName) +
        '&description=' + escape(self.documentDescription);
    $log.log('GET ' + q);
    $http.get(q).then(
      function(resp) {
        self.documents = resp.data;
        self.selectedDocument = null;
        $log.log('OK GET ' + q + ' (' + self.documents.length + ')');
      },
      function(resp) {
        $log.log('ERROR GET ' + q);
      }
    );
  }

  self.selectDocument = function(doc) {
    self.selectedDocument = doc;
    $log.log('Called selectDocument(' + doc.name + ')');
  }

  self.update = function() {
    $log.log('Called update()');
  }

  self.documentColor = function(document) {
    return (document == self.selectedDocument) ? 'yellow' : 'white';
  }

}]);