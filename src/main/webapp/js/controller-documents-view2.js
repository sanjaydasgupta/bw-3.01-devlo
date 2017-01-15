angular.module('BuildWhizApp')

.controller("ViewDocumentsCtrl", ['$log', '$http', 'AuthenticationService', '$window',
      function ($log, $http, AuthService, $window) {

  var self = this;

  self.documentCategories = ["ArchiCAD", "Architecture", "Building Science", "Civil", "Contracts",
      "Electrical", "Elevator", "GeoTech Fld Rpts", "Interior", "Material Specs", "Mechanical", "Permits",
      "Plumbing", "Reports", "Revit", "Special Insp Rpts", "Structure"];

  self.documentSubcategories = [];

  self.contentTypes = [];

  self.findModeActive = false;
  self.newModeActive = false;

  //self.documentList = [];
  self.documentCount = 0;
  self.currentContentKey = "Any";
  self.currentCategoryKey = "Any";
  self.currentSubcategoryKey = "Any";
  self.documentName = '';
  self.documentDescription = '';

  self.documents = [];
  self.selectedDocument = null;

  self.rfiList = [];
  self.selectedRfi = null;
  self.newSubject = '';
  self.newText = '';

  self.rfiDetails = [];

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
        self.currentSubcategoryKey = 'Any';
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

  self.fetchDocumentsByContent = function(contentKey) {
    $log.log('Setting content=' + contentKey);
    self.currentContentKey = contentKey;
    self.selectedDocument = null;
    self.documents = [];
    self.rfiList = [];
    self.selectedRfi = null;
  }

  self.findDocuments = function() {
    self.findModeActive = true;
    self.newModeActive = false;
    var q = 'baf/DocumentSearch?category=' + self.currentCategoryKey +
        '&subcategory=' + self.currentSubcategoryKey + '&content=' + self.currentContentKey +
        '&name=' + self.documentName + '&description=' + self.documentDescription;
    $log.log('GET ' + q);
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
        $log.log('OK GET ' + q + ' (' + self.documents.length + ')');
      },
      function(resp) {
        $log.log('ERROR GET ' + q)
      }
    )
  }

  self.fetchRfiList = function(rfi_ids) {
    var q = 'baf/RFIMessagesFetch?rfi_ids=' + rfi_ids.join(",");
    $log.log('GET ' + q);
    $http.get(q).then(
      function(resp) {
        self.rfiList = resp.data;
        self.selectedRfi = null;
        $log.log('OK GET ' + q + ' (' + self.rfiList.length + ')')
      },
      function(resp) {
        $log.log('ERROR GET ' + q)
      }
    )
  }

  self.selectDocument = function(document) {
    self.selectedDocument = document;

    if (self.selectedDocument.rfi_ids.length > 0) {
      self.fetchRfiList(self.selectedDocument.rfi_ids);
    } else {
      self.rfiList = [];
      self.selectedRfi = null;
    }
  }

  self.selectRfi = function(rfi) {
    $log.log('Called selectRfi(' + rfi + ')');

    if (rfi) {
      var query = 'baf/RFIDetailsFetch?person_id=' + AuthService.data._id + '&tz=' + AuthService.data.tz +
          '&rfi_id=' + rfi._id;
      $log.log('Calling GET ' + query);
      $http.get(query).then(
        function(resp) {
          self.rfiDetails = resp.data;
          self.selectedRfi = rfi;
          self.newSubject = rfi.subject;
          $log.log('OK GET ' + query)
        },
        function(resp) {
          //self.selectedMessage = null;
          $log.log('ERROR GET ' + query)
        }
      )
    } else {
      self.rfiDetails = [];
      self.selectedRfi = null;
      self.newSubject = '';
    }

  }

  self.submitRFI = function() {
    var query = 'baf/RFIMessageSubmit?person_id=' + AuthService.data._id + '&text=' + self.newText;
    if (self.selectedRfi == null) {
      query += '&subject=' + self.newSubject + '&document_id=' + self.selectedDocument._id +
          '&doc_version_timestamp=' + self.selectedDocument.timestamp;
    } else {
      query += '&rfi_id=' + self.selectedRfi._id;
    }
    $log.log('Calling POST ' + query);
    $http.post(query).then(
      function(resp) {
        self.newText = '';
        if (self.selectedRfi == null) {
          self.newSubject = '';
        }
        $log.log('OK POST ' + query)
      },
      function(resp) {
        $log.log('ERROR POST ' + query)
      }
    )
    $log.log('Called submitRfi()');
  }

  self.getRowColor = function(document) {
    return document == self.selectedDocument ? 'yellow' : ((document.version == 0) ? 'white' : 'lightgray');
  }

  self.isDataAdmin = function() {
    return AuthService.data.roles.join().indexOf('BW-Data-Admin') != -1;
  }

  self.sendDisabled = function() {
    return self.newSubject == '' || self.newText == '';
  }

}]);