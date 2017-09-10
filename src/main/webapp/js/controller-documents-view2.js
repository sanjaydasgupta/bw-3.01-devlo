angular.module('BuildWhizApp')

.controller("ViewDocumentsCtrl2", ['$log', '$http', 'AuthenticationService', '$window', '$routeParams',
      function ($log, $http, AuthService, $window, $routeParams) {

  var self = this;

  self.busy = false;
  self.showInfo = false;

  self.authors = [];
  self.filteredAuthors = [];
  self.NoAuthor = {name: 'Select', _id: ''};
  self.currentAuthor = self.NoAuthor;
  self.versionComments = '';
  self.selectedDate = new Date();

  self.dateOptions = {
    dateDisabled: false,
    formatYear: 'yy',
    maxDate: new Date(2018, 11, 31),
    minDate: new Date(2010, 0, 1),
    startingDay: 1
  };

  self.editableDocumentCategories = [];
  self.documentCategories = [];
  self.documentSubcategories = [];
  self.contentTypes = [];

  self.findModeActive = false;
  self.newModeActive = false;

  self.documentCount = 0;
  self.currentContentKey = "Any";
  self.currentCategoryKey = "[Select]";
  self.currentSubcategoryKey = "Any";
  self.documentName = '';
  self.documentDescription = '';

  self.displayAllVersions = false;
  self.documents = [];
  self.selectedDocument = null;

  self.selectedDocumentLabels = [];

  self.newSubject = '';
  self.newText = '';

  self.documentToDelete = null;
  self.documentToUpload = null;

  if ($routeParams.hasOwnProperty('origin1')) {
    self.initialCategoryKey = $routeParams.origin1;
    if ($routeParams.hasOwnProperty('origin2')) {
      self.initialSubcategoryKey = $routeParams.origin2;
      if ($routeParams.hasOwnProperty('origin3')) {
        self.initialDocumentName = $routeParams.origin3;
      }
    }
  }

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

  var query = 'api/DocumentCategory?role=edit';
  $http.get(query).then(
    function(resp) {
      self.editableDocumentCategories = resp.data.map(function(c) {return c.category;});
      $log.log('OK GET ' + query + ' (' + self.editableDocumentCategories.length + ')');
    },
    function(resp) {
      $log.log('ERROR: ' + query);
    }
  )

  self.toggleInfoDisplay = function() {
    self.showInfo = !self.showInfo;
    $log.log('Calling toggleInfoDisplay: ' + self.showInfo);
  }

  self.busy = true;
  $http.get('api/ContentType').then(
    function(resp) {
      self.contentTypes = resp.data.map(function(p) {
        var contentType = p.type;
        return contentType;
      });
      $log.log('OK GET api/ContentType (' + self.contentTypes + ')');
      self.busy = false;
      //self.documentToDelete = null;
    },
    function(resp) {
      $log.log('ERROR GET api/contentType')
      self.busy = false;
    }
  )

  self.busy = true;
  $http.get('api/DocumentCategory').then(
    function(resp) {
      self.documentCategories = resp.data.map(function(p) {return p.category;});
      $log.log('OK GET api/DocumentCategory (' + self.documentCategories.length + ')');
      self.busy = false;
      //self.documentToDelete = null;
    },
    function(resp) {
      $log.log('ERROR GET api/DocumentCategory')
      self.busy = false;
    }
  )

  self.resetDisplay = function() {
    self.selectedDocument = null;
    self.documents = [];
    self.documentToDelete = null;
    self.documentToUpload = null;
  }

  self.fetchDocumentsByCategory = function(categoryKey, subcategoryKey) {
    var query = 'baf/DocumentSubcategoriesFetch?category=' + categoryKey;
    $log.log('calling GET ' + query);
    self.busy = true;
    $http.get(query).then(
      function(resp) {
        self.documentSubcategories = resp.data;
        self.currentSubcategoryKey = 'Any';
        self.resetDisplay();
        $log.log('OK GET ' + query + ' (' + resp.data.length + ')');
        self.busy = false;
        self.documentToDelete = null;
        self.documentToDelete = null;
        if (subcategoryKey) {
          self.fetchDocumentsBySubcategory(subcategoryKey, true);
        }
      },
      function(resp) {
        $log.log('ERROR GET ' + query);
        self.busy = false;
      }
    );
    self.currentCategoryKey = categoryKey;
  }

  self.fetchDocumentsBySubcategory = function(subcategoryKey, fetch) {
    $log.log('Setting subcategory=' + subcategoryKey);
    self.currentSubcategoryKey = subcategoryKey;
    self.resetDisplay();
    if (fetch) {
      self.findDocuments();
    }
  }

  self.fetchDocumentsByContent = function(contentKey) {
    $log.log('Setting content=' + contentKey);
    self.currentContentKey = contentKey;
    self.resetDisplay();
  }

  self.allVersionsChanged = function() {
    self.resetDisplay();
  }

  self.findDocuments = function() {
    self.findModeActive = true;
    self.newModeActive = false;
    var q = 'baf/DocumentSearch?category=' + self.currentCategoryKey +
        '&subcategory=' + self.currentSubcategoryKey + '&content=' + self.currentContentKey +
        '&name=' + self.documentName + '&description=' + self.documentDescription;
    q += self.displayAllVersions ? '&versions=all' : '&versions=latest'
    $log.log('GET ' + q);
    self.busy = true;
    $http.get(q).then(
      function(resp) {
        self.documents = resp.data;
        self.selectedDocument = null;
        self.documentToDelete = null;
        self.busy = false;
        self.documentToUpload = null;
        $log.log('OK GET ' + q + ' (' + self.documents.length + ')');
      },
      function(resp) {
        $log.log('ERROR GET ' + q)
        self.busy = false;
      }
    )
  }

  self.getRowColor = function(document) {
    return document == self.selectedDocument ? 'yellow' : ((document.version == 0) ? 'white' : 'lightgray');
  }

  self.sendDisabled = function() {
    return self.newSubject == '' || self.newText == '';
  }

  self.specialFunctionsDisplayed = function() {
    return self.displayAllVersions && self.isPrabhas();
  }

  self.isPrabhas = function() {
    return AuthService.data._id == '56f124dfd5d8ad25b1325b3e';
  }

  self.canUpload = function() {
    var mayEdit = self.editableDocumentCategories.indexOf(self.currentCategoryKey) != -1;
    $log.log('mayEdit: ' + mayEdit);
    return self.isPrabhas() || mayEdit;
  }

  self.deleteDocument = function(doc) {
    self.documentToDelete = doc;
    $log.log('Called deleteDocument(' + doc._id + ')');
  }

  self.deleteDocumentConfirmed = function() {
    var idToDelete = self.documentToDelete._id;
    var tsToDelete = self.documentToDelete.timestamp;
    $log.log('Called deleteDocumentConfirmed(' + self.documentToDelete._id + ')');
    var q = 'baf/DocumentVersionDelete?document_id=' + idToDelete + '&timestamp=' + tsToDelete;
    self.busy = true;
    $http.post(q).then(
      function(resp) {
        self.documents = self.documents.filter(function(d){return d._id != idToDelete || d.timestamp != tsToDelete;});
        $log.log('OK POST ' + q);
        self.busy = false;
        self.documentToDelete = null;
      },
      function(resp) {
        $log.log('ERROR POST ' + q)
        self.busy = false;
      }
    )
    self.documentToDelete = null;
  }

  self.isDataAdmin = function() {
    var roles = AuthService.data.roles.join(',');
    return roles.indexOf('BW-Admin') != -1 || roles.indexOf('BW-Data-Admin') != -1;
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

  self.origin = function(documentName) {
    return 'origin0=documents-view2&origin1=' + self.currentCategoryKey +
        '&origin2=' + self.currentSubcategoryKey + '&origin3=' + documentName;
  }

  self.uploadDocumentVersion = function(doc) {
    $log.log('Called uploadDocumentVersion2()');
    self.documentToUpload = doc;
    self.currentAuthor = self.authors.filter(function(a){return a._id == doc.author_person_id})[0];
    $log.log('Exiting uploadDocumentVersion2()');
  }

  self.startUpload = function(doc) {
    $log.log('Called startUpload()');
    var uploadButton = $window.document.getElementById('version-upload-button');
    uploadButton.addEventListener('change', self.performUpload, false);
    uploadButton.click();
    $log.log('Exiting startUpload()');
  }

  self.performUpload = function(evt) {
    $log.log('Called performUpload()');
    var uploadButton = $window.document.getElementById('version-upload-button');
    uploadButton.removeEventListener('change', self.performUpload, false);
    var files = evt.target.files; // FileList of File objects
    if (files.length > 0) {
      var timestamp = self.selectedDate.getTime();
      var formData = new FormData();
      angular.forEach(files, function(file, index) {
        formData.append(file.name, file, file.name);
        $log.log('formData.append(' + file.name + ')');
      });
      var query = 'baf/DocumentPreload?person_id=' + AuthService.data._id + '&timestamp=' + timestamp +
          '&comments=' + escape(self.versionComments) + '&author_person_id=' + self.currentAuthor._id +
          '&document_master_id=' + self.documentToUpload._id;
      $log.log("POST: " + query);
      self.busy = true;
      $http.post(query, formData, {transformRequest: angular.identity, headers: {'Content-Type': undefined}}).then(
        function() {
          $log.log('OK ' + query);
          self.busy = false;
          //alert('OK: Uploading ' + files.length + ' files completed');
        },
        function() {
          $log.log('ERROR ' + query);
          self.busy = false;
          alert('ERROR: Uploading ' + files.length + ' files failed');
        }
      )
    }
    $log.log('Exiting performUpload()');
  }

  self.displayDetail = function(document) {
    var query = 'baf/DocumentLabelsFetch';
    $log.log('GET ' + query);
    $http.get(query).then(
      function(res) {
        self.selectedDocumentLabels = res.data.map(function(label) {
          var ok = label.document_ids.includes(self.selectedDocument._id);
          return {name: label.name, contained: ok};
        });
        $log.log('OK GET ' + query + ' (' + self.selectedDocumentLabels.length + ' labels)');
      },
      function(res) {
        alert('ERROR GET ' + query);
      }
    )
    self.selectedDocument = document;
  }

  self.labelToggle = function(label) {
    var query = 'baf/DocumentLabelManage?label_name=' + label.name + '&document_id=' + self.selectedDocument._id +
        '&op=' + (label.contained ? 'add' : 'remove');
    $log.log('POST ' + query);
    $http.post(query).then(
      function(res) {
        $log.log('OK POST ' + query);
      },
      function(res) {
        alert('ERROR POST ' + query);
      }
    )
  }

  if (self.initialDocumentName) {
    self.fetchDocumentsByCategory(self.initialCategoryKey, self.initialSubcategoryKey);
  }

}]);