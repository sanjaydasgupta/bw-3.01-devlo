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

  self.documentList = [];
  self.documentCount = 0;
  self.currentContentKey = "Any";
  self.currentCategoryKey = "Any";
  self.currentSubcategoryKey = "Any";
  self.documentName = '';
  self.documentDescription = '';

  self.records = [];
  self.recordSelected = false;
  self.selectedRecord = null;
  self.versions = [];

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

  self.today = function() {
    return new Date().toLocaleDateString();
  }

  self.fetchDocumentsByCategory = function(categoryKey) {
    var query = 'baf/DocumentSubcategoriesFetch?category=' + categoryKey;
    $log.log('calling GET ' + query);
    $http.get(query).then(
      function(resp) {
        self.documentSubcategories = resp.data;
        self.currentSubcategoryKey = 'Any';
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

  self.fetchDocumentsByContent = function(contentKey) {
    $log.log('Setting content=' + contentKey);
    self.currentContentKey = contentKey;
  }

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

  self.isDataAdmin = function() {
    return AuthService.data.roles.join().indexOf('BW-Data-Admin') != -1;
  }

}]);