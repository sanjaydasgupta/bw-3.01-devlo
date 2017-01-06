angular.module('BuildWhizApp')

.controller("MongodbCtrl", ['$http', '$log', function ($http, $log) {
  var self = this;
  self.collections = [];
  self.details = [];
  self.name = ""
  self.archiveStatus = {status: -1};

  $log.log('MongodbCtrl: calling GET baf/MongoDBView');
  $http.get('baf/MongoDBView').then(
    function(resp) {
      var newCollections = [];
      resp.data.forEach(function(coll) {
        if (coll.name != 'trace_log') {
          newCollections.push(coll);
        }
      })
      self.collections = newCollections;
    },
    function(errResponse) {alert("MongodbCtrl: ERROR(collections): " + errResponse);}
  );

  self.displayCollection = function(name) {
    query = '?collection_name=' + name;
    $log.log('MongodbCtrl: GET baf/MongoDBView' + query);
    $http.get('baf/MongoDBView' + query).then(
      function(resp) {
        self.details = resp.data;
        self.name = name;
      },
      function(errResponse) {alert("MongodbCtrl: ERROR(collection-details): " + errResponse);}
    );
  }

  self.archive = function() {
    query = 'baf/MongoDBView?collection_name=*';
    $log.log('MongodbCtrl: GET ' + query);
    $http.get(query).then(
      function(resp) {
        self.archiveStatus = resp.data;
      },
      function(errResponse) {alert("MongodbCtrl: ERROR(collection-details): " + errResponse);}
    );
  }

}]);

