angular.module('BuildWhizApp')

.controller("MongodbCtrl", ['$http', '$log', function ($http, $log) {
  var self = this;
  self.collections = [];
  self.details = [];
  self.name = ""
  self.archiveStatus = {status: -1};
  self.displayingSchema = false;

  self.query = '';
  self.projection = '';

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
    var q = 'baf/MongoDBView?collection_name=' + name;
    $log.log('MongodbCtrl: GET baf/MongoDBView' + q);
    $http.get(q).then(
      function(resp) {
        self.details = resp.data.map(function(d){return JSON.stringify(d, null, 1);});
        self.name = name;
        self.displayingSchema = false;
      },
      function(errResponse) {alert("MongodbCtrl: ERROR(collection-details): " + errResponse);}
    );
    self.name = '';
    self.query = '';
  }

  self.displaySchema = function(name) {
    var q = 'baf/MongoDBView?collection_name=' + name + '*';
    $log.log('MongodbCtrl: GET ' + q);
    $http.get(q).then(
      function(resp) {
        self.details = resp.data;
        self.name = name;
        self.displayingSchema = true;
      },
      function(errResponse) {alert("MongodbCtrl: ERROR(collection-details): " + errResponse);}
    );
    self.name = '';
    self.query = '';
  }

  self.archive = function() {
    var q = 'baf/MongoDBView?collection_name=*';
    $log.log('MongodbCtrl: GET ' + q);
    $http.get(q).then(
      function(resp) {
        $log.log('MongoDB archive status: ' + JSON.stringify(resp.data));
        self.archiveStatus = resp.data;
      },
      function(errResponse) {alert("MongodbCtrl: ERROR(collection-details): " + errResponse);}
    );
    self.name = '';
    self.query = '';
  }

  self.runQuery = function() {
    $log.log('Called runQuery()');
    var q = 'baf/MongoDBView?collection_name=' + escape(self.name) + '&query=' + escape(self.query);
    if (self.projection != '') {
      q += '&fields=' + escape(self.projection);
    }
    $log.log('MongodbCtrl: GET ' + q);
    $http.get(q).then(
      function(resp) {
        self.details = resp.data;
        self.displayingSchema = false;
      },
      function(errResponse) {alert("MongodbCtrl: ERROR(collection-details): " + errResponse);}
    );
  }

}]);

