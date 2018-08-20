angular.module('BuildWhizApp')

.controller("LoaderDocumentsCtrl", ['$log', '$http', 'AuthenticationService', '$window',
      function ($log, $http, AuthService, $window) {

  var self = this;

  self.busy = false;

  self.documentSubcategories = [];

  self.contentTypes = [];
  self.authors = [];
  self.filteredAuthors = [];
  self.NoAuthor = {name: 'Select', _id: ''};
  self.currentAuthor = self.NoAuthor;

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

  self.currentOperationKey = 'Files Upload';
  self.useTextInSearch = false;

  self.dateOptions = {
    dateDisabled: false,
    formatYear: 'yy',
    maxDate: new Date(2018, 11, 31),
    minDate: new Date(2010, 0, 1),
    startingDay: 1
  };

  self.lastSearchQuery = '';

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

  $http.get('api/DocumentCategory').then(
    function(resp) {
      self.documentCategories = resp.data.map(function(p) {return p.category;});
      $log.log('OK GET api/DocumentCategory (' + self.documentCategories.length + ')');
    },
    function(resp) {
      $log.log('ERROR GET api/DocumentCategory')
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

  self.metadataFieldsDisabled = function() {
    return self.isVersioned() && self.currentOperationKey == 'Files Upload';
  }

  self.isVersioned = function() {
    return self.selectedDocument != null/* && self.currentCategoryKey == self.selectedDocument.category &&
        self.currentSubcategoryKey == self.selectedDocument.subcategory &&
        self.documentName == self.selectedDocument.name &&
        self.documentDescription == self.selectedDocument.description*/;
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
      if (self.isVersioned()) {
        query += '&document_master_id=' + self.selectedDocument._id;
      }
      $log.log("POST: " + query);
      self.busy = true;
      $http.post(query, formData, {transformRequest: angular.identity, headers: {'Content-Type': undefined}}).then(
        function() {
          $log.log('OK ' + query);
          self.busy = false;
          alert('OK: Uploading ' + files.length + ' files completed');
        },
        function() {
          $log.log('ERROR ' + query);
          self.busy = false;
          alert('ERROR: Uploading ' + files.length + ' files failed');
        }
      )
    }
    $log.log('Exiting uploadBegin()');
  }

  self.findDisabled = function() {
    return self.currentCategoryKey == 'Select';
  }

  self.findDocuments = function() {
    self.lastSearchQuery = 'baf/DocumentSearch?category=' + escape(self.currentCategoryKey) +
        '&subcategory=' + escape(self.currentSubcategoryKey == 'Other' ? self.subcategoryText : self.currentSubcategoryKey) +
        (self.currentAuthor._id == '' ? '' : ('&content=Any&author_person_id=' + self.currentAuthor._id));
    if (self.useTextInSearch) {
      self.lastSearchQuery += '&name=' + escape(self.documentName) +
      '&description=' + escape(self.documentDescription) + '&comments=' + escape(self.versionComments);
    }
    $log.log('GET ' + self.lastSearchQuery);
    self.busy = true;
    $http.get(self.lastSearchQuery).then(
      function(resp) {
        self.documents = resp.data;
        self.selectedDocument = null;
        $log.log('OK GET ' + self.lastSearchQuery + ' (' + self.documents.length + ')');
        self.busy = false;
      },
      function(resp) {
        $log.log('ERROR GET ' + self.lastSearchQuery);
        self.busy = false;
      }
    );
  }

  self.refreshDocuments = function() {
    $log.log('GET ' + self.lastSearchQuery);
    self.busy = true;
    $http.get(self.lastSearchQuery).then(
      function(resp) {
        self.documents = resp.data;
        self.selectedDocument = null;
        $log.log('OK GET ' + self.lastSearchQuery + ' (' + self.documents.length + ')');
        self.busy = false;
      },
      function(resp) {
        $log.log('ERROR GET ' + self.lastSearchQuery);
        self.busy = false;
      }
    );
  }

  self.selectDocument = function(doc) {
    self.selectedDocument = doc;
    self.currentCategoryKey = doc.category;
    self.currentSubcategoryKey = doc.subcategory;
    self.documentName = doc.name;
    self.documentDescription = doc.description;
    self.versionComments = doc.comments;
    self.currentAuthor = self.authors.filter(function(a){return a._id == doc.author_person_id;})[0];
    var newTime = new Date(doc.timestamp);
    self.selectedDate = newTime;
    $log.log('Called selectDocument(' + doc.name + ')');
  }

  self.updateDisabled = function() {
//    var subcategory = self.currentSubcategoryKey == 'Other' ? self.subcategoryText : self.currentSubcategoryKey;
//    return self.selectedDocument == null ||
//        (self.currentCategoryKey == self.selectedDocument.category &&
//        subcategory == self.selectedDocument.subcategory &&
//        self.documentName == self.selectedDocument.name &&
//        self.documentDescription == self.selectedDocument.description &&
//        self.currentAuthor._id == self.selectedDocument.author_person_id &&
//        self.selectedDate.getTime() == self.selectedDocument.timestamp &&
//        self.versionComments == self.selectedDocument.comments);
    return self.selectedDocument == null || self.documents.filter(function(d){return d.isSelected;}).length == 0;
  }

  self.update = function() {
    var docIds = self.documents.filter(function(d){return d.isSelected;}).
        map(function(d){return {document_master_id: d._id, timestamp: d.timestamp,
        category: self.currentCategoryKey, subcategory: self.currentSubcategoryKey,
        name: self.documentName, description: self.documentDescription,
        author_person_id: self.currentAuthor._id, comments: self.versionComments};});

    var q = 'baf/DocumentMetadataUpdate';
    $log.log('POST ' + q + ') ' + JSON.stringify(docIds));
    self.busy = true;
    $http.post(q, docIds).then(
      function(resp) {
        self.refreshDocuments();
        self.busy = false;
        $log.log('OK POST ' + q + ')');
      },
      function(resp) {
        self.busy = false;
        $log.log('ERROR POST ' + q);
      }
    );
  }

  self.update2 = function() {
    var q = 'baf/DocumentMetadataUpdate?document_master_id=' + self.selectedDocument._id;
    if (self.currentCategoryKey != self.selectedDocument.category) {
      q += '&category=' + escape(self.currentCategoryKey);
    }
    var subcategory = self.currentSubcategoryKey == 'Other' ? self.subcategoryText : self.currentSubcategoryKey;
    if (subcategory != self.selectedDocument.subcategory) {
      q += '&subcategory=' + escape(subcategory);
    }
    if (self.documentName != self.selectedDocument.name) {
      q += '&name=' + escape(self.documentName);
    }
    if (self.documentDescription != self.selectedDocument.description) {
      q += '&description=' + escape(self.documentDescription);
    }
    var needTimestamp = false;
    if (self.currentAuthor._id != self.selectedDocument.author_person_id) {
      q += '&author_person_id=' + self.currentAuthor._id;
      needTimestamp = true;
    }
    //if (self.selectedDate.getTime() != self.selectedDocument.timestamp) {
    //  q += '&timestamp=' + self.selectedDate.getTime();
    //}
    if (self.versionComments != self.selectedDocument.comments) {
      q += '&comments=' + escape(self.versionComments);
      needTimestamp = true;
    }
    if (needTimestamp) {
      q += '&timestamp=' + self.selectedDocument.timestamp;
    }
    $log.log('POST ' + q);
    $http.post(q).then(
      function(resp) {
        $log.log('OK POST ' + q + ')');
        self.refreshDocuments();
      },
      function(resp) {
        $log.log('ERROR POST ' + q);
      }
    );
  }

  self.documentColor = function(document) {
    return (document == self.selectedDocument) ? 'yellow' : (document.version == 0) ? 'white' : 'lightgray';
  }

  self.reset = function(document) {
    self.resetClassMetaData();
    self.resetVersionMetaData();
    self.documents = [];
    self.selectedDocument = null;
    $log.log('Called reset()');
  }

  self.findRfiDestinationDisabled = function() {
    return self.currentCategoryKey == 'Select' || self.currentSubcategoryKey == 'Other';
  }

  self.findRfiDestination = function() {
    var q = 'api/RFIDestination?category=' + self.currentCategoryKey + '&subcategory=' + self.currentSubcategoryKey;
    $log.log('Calling findRfiDestination(GET ' + q + ')');
    self.busy = true;
    $http.get(q).then(
      function(resp) {
        var persons = resp.data;
        if (persons.length == 0) {
          self.currentAuthor = self.NoAuthor;
        } else {
          $log.log(JSON.stringify(persons))
          var id = persons[0].person_id;
          self.currentAuthor = self.authors.filter(function(a){return a._id == id;})[0];
        }
        $log.log('OK findRfiDestination(GET ' + q + ')');
        self.busy = false;
      },
      function(resp) {
        $log.log('ERROR findRfiDestination(GET ' + q + ')');
        self.busy = false;
      }
    )
  }

  self.setRfiDestinationDisabled = function() {
    return self.currentCategoryKey == 'Select' || self.currentSubcategoryKey == 'Other' || self.currentAuthor._id == '';
  }

  self.setRfiDestination = function() {
    var q = 'api/RFIDestination'
    var data = {category: self.currentCategoryKey, subcategory: self.currentSubcategoryKey,
        person_id: self.currentAuthor._id};
    $log.log('Calling setRfiDestination(POST ' + q);
    self.busy = true;
    $http.post(q, data).then(
      function(resp) {
        $log.log('OK setRfiDestination(POST ' + q);
        self.busy = false;
      },
      function(resp) {
        $log.log('ERROR setRfiDestination(POST ' + q);
        self.busy = false;
      }
    )
  }

  self.setOperation = function(op) {
    self.currentOperationKey = op;
    $log.log('Called setOperation(' + op + ')');
  }

}]);