app.controller("AmazonS3Ctrl", function ($http, $log) {

    var self = this;
    self.amazonDocs = [];
    self.isWaiting = true;
    self.personId = document.getElementById("hndLoggedInID").value;

    var query = 'person_id=' + self.personId
    $http.get('baf/AmazonS3Docs?' + query).then(
        function (response) {
            self.amazonDocs = response.data;
            self.isWaiting = false;
        }
    );

});