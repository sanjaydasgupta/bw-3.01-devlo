angular.module('BuildWhizApp')

.controller("MongodbCtrl", ['$http', '$log', function ($http, $log) {
    var self = this;
    self.collections = [];
    self.details = [];
    self.name = ""

    $log.log('MongodbCtrl: calling GET baf/MongoDBView');
    $http.get('baf/MongoDBView').then(
        function(resp) {
            self.collections = resp.data;
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
}]);

