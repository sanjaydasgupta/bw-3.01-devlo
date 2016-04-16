app.controller("BuildWhizNgAppMongodbCtrl", function ($scope, $http, $log) {
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
        query = '?collection_name=' + name;
        $log.log('calling GET baf/MongoDBView' + query);
        $http.get('baf/MongoDBView' + query).then(
            function(resp) {
                self.details = resp.data;
                self.name = name;
            },
            function(errResponse) {alert("ERROR(collection-details): " + errResponse);}
        );
    }
});

