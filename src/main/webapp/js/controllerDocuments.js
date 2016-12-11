angular.module('BuildWhizApp')

.controller("DocumentsCtrl", ['$log', '$http', 'AuthenticationService', function ($log, $http, AuthService) {

  var self = this;

  self.documentList = [];
  self.currentFilterKey = 'All';
  self.currentTypeKey = 'Any';
  self.currentSkillKey = 'Any';
  self.currentContentKey = 'Any';
  self.currentAuthorKey = 'Any';

  self.fetchDocumentsByType = function(typeKey) {
    self.currentTypeKey = typeKey;
    self.currentSkillKey = 'Any';
    self.currentContentKey = 'Any';
    self.currentAuthorKey = 'Any';
  }

  self.fetchDocumentsBySkill = function(skillKey) {
    self.currentSkillKey = skillKey;
    self.currentContentKey = 'Any';
    self.currentAuthorKey = 'Any';
  }

  self.fetchDocumentsByContent = function(contentKey) {
    self.currentContentKey = contentKey;
    self.currentAuthorKey = 'Any';
  }

  self.fetchDocumentsByAuthor = function(authorKey) {
    self.currentAuthorKey = authorKey;
  }

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

}]);