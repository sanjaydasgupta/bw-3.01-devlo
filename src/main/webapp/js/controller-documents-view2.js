angular.module('BuildWhizApp')

.controller("ViewDocumentsCtrl2", ['$log', '$http', 'AuthenticationService', '$window',
      function ($log, $http, AuthService, $window) {

  var self = this;

  self.busy = false;
  self.displayMode = 'DOC';

  self.documentCategories = [];
  self.documentSubcategories = [];
  self.contentTypes = [];

  self.findModeActive = false;
  self.newModeActive = false;

  self.documentCount = 0;
  self.currentContentKey = "Any";
  self.currentCategoryKey = "Any";
  self.currentSubcategoryKey = "Any";
  self.documentName = '';
  self.documentDescription = '';

  self.displayAllVersions = false;
  self.documents = [];
  self.selectedDocument = null;

  self.rfiList = [];
  self.selectedRfi = null;
  self.newSubject = '';
  self.newText = '';
  self.rfiDetails = [];
  self.rfiAttachments = [];

  self.documentToDelete = null;

  self.busy = true;
  $http.get('api/ContentType').then(
    function(resp) {
      self.contentTypes = resp.data.map(function(p) {
        var contentType = p.type;
        return contentType;
      });
      $log.log('OK GET api/ContentType (' + self.contentTypes + ')');
      self.busy = false;
      self.documentToDelete = null;
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
      self.documentToDelete = null;
    },
    function(resp) {
      $log.log('ERROR GET api/DocumentCategory')
      self.busy = false;
    }
  )

  self.resetDisplay = function() {
    self.selectedDocument = null;
    self.documents = [];
    self.rfiList = [];
    self.selectedRfi = null;
  }

  self.fetchDocumentsByCategory = function(categoryKey) {
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
      },
      function(resp) {
        $log.log('ERROR GET ' + query);
        self.busy = false;
      }
    );
    self.currentCategoryKey = categoryKey;
  }

  self.fetchDocumentsBySubcategory = function(subcategoryKey) {
    $log.log('Setting subcategory=' + subcategoryKey);
    self.currentSubcategoryKey = subcategoryKey;
    self.resetDisplay();
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
        if (resp.data.hasOwnProperty('rfiList')) {
          self.rfiList = rfiList;
        } else {
          self.rfiList = [];
        }
        self.selectedRfi = null;
        self.documentToDelete = null;
        $log.log('OK GET ' + q + ' (' + self.documents.length + ')');
        self.busy = false;
        self.documentToDelete = null;
      },
      function(resp) {
        $log.log('ERROR GET ' + q)
        self.busy = false;
      }
    )
  }

  self.fetchRfiList = function(rfi_ids) {
    var q = 'baf/RFIMessagesFetch?rfi_ids=' + rfi_ids.join(",");
    $log.log('GET ' + q);
    self.busy = true;
    $http.get(q).then(
      function(resp) {
        self.rfiList = resp.data;
        self.selectedRfi = null;
        $log.log('OK GET ' + q + ' (' + self.rfiList.length + ')')
        self.busy = false;
        self.documentToDelete = null;
      },
      function(resp) {
        $log.log('ERROR GET ' + q)
        self.busy = false;
      }
    )
  }

  self.refreshRfiList = function() {
    self.fetchRfiList(self.selectedDocument.rfi_ids);
  }

  self.selectDocument = function(document) {
    self.selectedDocument = document;
    if (self.selectedDocument.rfi_ids.length > 0) {
      self.fetchRfiList(self.selectedDocument.rfi_ids);
    } else {
      self.rfiList = [];
      self.selectedRfi = null;
    }
    self.displayMode = 'RFI';
  }

  self.rfiHeader = function() {
    var d = self.selectedDocument;
    var docName = 'RFI for [' + [d.category, d.subcategory, d.name, d.description].join('/') + ']';
    return docName + ' of ' + self.selectedDocument.date_time + ' by ' + self.selectedDocument.author;
  }

  self.selectRfi = function(rfi) {
    $log.log('Called selectRfi(' + rfi + ')');

    if (rfi) {
      var query = 'baf/RFIDetailsFetch?person_id=' + AuthService.data._id + '&tz=' + AuthService.data.tz +
          '&rfi_id=' + rfi._id;
      $log.log('Calling GET ' + query);
      self.busy = true;
      $http.get(query).then(
        function(resp) {
          self.rfiDetails = resp.data;
          self.selectedRfi = rfi;
          self.newSubject = rfi.subject;
          $log.log('OK GET ' + query)
          self.busy = false;
        },
        function(resp) {
          //self.selectedMessage = null;
          $log.log('ERROR GET ' + query)
          self.busy = false;
        }
      )
    } else {
      self.rfiDetails = [];
      self.selectedRfi = null;
      self.newSubject = '';
    }

  }

  self.backToDocuments = function() {
    self.displayMode = 'DOC';
  }

  self.submitRFI = function() {
    var query = 'baf/RFIMessageSubmit?person_id=' + AuthService.data._id + '&text=' + escape(self.newText) +
        '&attachments=' + escape(self.rfiAttachments.map(function(a){return JSON.stringify(a);}).join('#'));
    if (self.selectedRfi == null) {
      query += '&subject=' + escape(self.newSubject) + '&document_id=' + self.selectedDocument._id +
          '&doc_version_timestamp=' + self.selectedDocument.timestamp;
    } else {
      query += '&rfi_id=' + self.selectedRfi._id;
    }
    $log.log('Calling POST ' + query);
    self.busy = true;
    $http.post(query).then(
      function(resp) {
        self.newText = '';
        if (self.selectedRfi == null) {
          self.newSubject = '';
        }
        $log.log('OK POST ' + query)
        self.busy = false;
      },
      function(resp) {
        $log.log('ERROR POST ' + query)
        self.busy = false;
      }
    )
    $log.log('Called submitRfi()');
  }

  self.getRowColor = function(document) {
    return document == self.selectedDocument ? 'yellow' : ((document.version == 0) ? 'white' : 'lightgray');
  }

  self.sendDisabled = function() {
    return self.newSubject == '' || self.newText == '';
  }

  self.deleteDocumentDisplayed = function() {
    return self.displayAllVersions && AuthService.data._id == '56f124dfd5d8ad25b1325b3e';
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

  self.attachFile = function() {
    $log.log('Called attachFile()');
    var attachButton = $window.document.getElementById('attach-file-button');
    attachButton.addEventListener('change', self.attachmentUploadBegin, false);
    attachButton.click();
    $log.log('Exiting attachFile()');
  }

  self.attachmentUploadBegin = function(evt) {
    $log.log('Called uploadBegin()');
    var attachButton = $window.document.getElementById('attach-file-button');
    attachButton.removeEventListener('change', self.uploadBegin, false);
    var files = evt.target.files; // FileList of File objects
    if (files.length > 0) {
      var timestamp = new Date().getTime();
      var formData = new FormData();
      angular.forEach(files, function(file, index) {
        formData.append(file.name, file, file.name);
        $log.log('formData.append(' + file.name + ')');
      });
      var desc = self.selectedDocument._id + '/' + self.selectedDocument.timestamp + '/' + timestamp;
      var query = 'baf/DocumentPreload?person_id=' + AuthService.data._id + '&timestamp=' + timestamp +
          '&author_person_id=' + AuthService.data._id + '&category=SYSTEM' + '&subcategory=RFI-Attachment' +
          '&name=' + escape(self.documentName) + '&description=' + escape(desc);
      $log.log("POST: " + query);
      self.busy = true;
      $http.post(query, formData, {transformRequest: angular.identity, headers: {'Content-Type': undefined}}).then(
        function(resp) {
          $log.log('OK ' + query);
          self.busy = false;
          self.rfiAttachments.push(resp.data[0]);
          alert('OK: Uploading: ' + JSON.stringify(resp.data[0]));
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

}]);