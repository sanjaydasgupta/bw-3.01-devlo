app.controller("DocumentsCtrl", function ($log, $http) {

    var self = this;
    var personId = document.getElementById("hndPersonID").value;
    self.projects = [];
    self.documents = [];
    self.isWaiting = true;

    self.getPersonId = function() {
        return personId;
    }

    var param = '?person_id=' + personId;
    $http.get('baf/OwnedProjects' + param).then(
        function(response) {
            self.projects = response.data;
            self.isWaiting = false;
        }
    )

    self.projectSelected = function() {
        self.isWaiting = true;
        var param = '?project_id=' + self.selectedProjectId;
        $http.get('baf/PreloadedDocumentsList' + param).then(
            function(response) {
                self.documents = response.data;
                self.isWaiting = false;
            },
            function() {
                self.isWaiting = false;
            }
        )
    }

    self.getSelectedProjectId = function() {
        return self.selectedProjectId;
    }

    self.getDownloadLink = function(drawing) {
        return 'baf/DocumentDownload?document_id=' + drawing._id + '&project_id=' + self.selectedProjectId;
    }

});

app.directive("bwUpload2", ['$log', '$http', function ($log, $http) {
    $log.log('Entered bwUpload2 directive function');
    return {

        scope: { projectid: "=", documentid: "=", ngcontroller: "="},

        link: function (scope, element, attributes) {
            element.bind("change", function (changeEvent) {
                var reader = new FileReader();
                reader.onload = function (loadEvent) {
                    $log.log('onload.byteLength: ' + loadEvent.target.result.byteLength);
                    scope.$apply(function () {
                        var params = 'project_id=' + scope.projectid + '&document_id=' + scope.documentid;
                        $log.log('SDG-params: ' + params);
                        $log.log('SDG-attributes: ' + JSON.stringify(attributes));
                        var config = {
                            url: 'baf/DocumentUpload?' + params,
                            method: 'POST',
                            headers: { 'Content-Type': 'application/octet-stream' },
                            data: new Uint8Array(loadEvent.target.result),
                            transformRequest: []
                        };
                        $log.log('scope.ngcontroller.isWaiting = true;');
                        scope.ngcontroller.isWaiting = true;
                        $http(config).then(
                            function (resp) {
                                scope.ngcontroller.isWaiting = false;
                                var response = resp.data;
                                $log.log("DocumentUpload response (fileName, length): " + response.fileName + ", " + response.length);
                                alert('Document Saved');
                            },
                            function (responseError) {
                                scope.ngcontroller.isWaiting = false;
                                $(".msgToggle").show();
                                document.getElementById('lblMsg').innerHTML = serviceErrorMessege;
                            }
                        );
                    });

                }
                reader.readAsArrayBuffer(changeEvent.target.files[0]);
            });
        }
    }
}]);

