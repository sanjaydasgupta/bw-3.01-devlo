app.controller("BuildWhizNgAppMongodbCtrl", function ($http, $log) {
    var self = this;
    self.collections = [];
    self.details = [];
    self.name = ""

    $log.log('calling GET baf/MongoDBView');
    $http.get('baf/MongoDBView').then(
        function(resp) {
            self.collections = resp.data;
        },
        function(errResponse) {alert("ERROR(collections): " + errResponse);}
    );

    self.displayCollection = function(name) {
      query = 'baf/MongoDBView?collection_name=' + name;
      $log.log('calling GET ' + query);
      $http.get(query).then(
        function(resp) {
          self.details = resp.data.map(function(d){return JSON.stringify(d, null, 1);});
          self.name = name;
          $log.log('OK GET ' + query);
        },
        function(errResponse) {
          alert("ERROR(collection-details): " + errResponse);
          $log.log('ERROR GET ' + query);
        }
      );
    }
});

