app.controller("RFICtrl", function ($http, $log) {
    var self = this;

    self.personId = document.getElementById("hndLoggedInID").value;
    self.ownedActions = [];
    self.rfiDocuments = [];
    self.selectedAction = null;
    self.isWaiting = false;

    $http.get('baf/OwnedActionsAll?person_id=' + self.personId).then(
        function (response) {
            self.ownedActions = response.data;
        }
    )

    self.statusColor = function (action) {
        switch (action.status) {
        case "defined":
            return "yellow";
            break;
        case "waiting":
            return "magenta";
            break;
        case "ended":
            return "gray";
            break;
        default:
            return "red";
            break;
        }
    }

    self.actionLabel = function (action) {
        if (action.type == "main") {
            return action.name;
        } else {
            return action.activity_name + " / " + action.name;
        }
    }

    self.actionSelected = function (action) {
        self.rfiDocuments = [];
        self.selectedAction = action;
        self.isWaiting = true;
        var query = '?project_id=' + action.project_id + '&activity_id=' + action.activity_id +
            '&action_name=' + action.name;
        $http.get('baf/RfiDocuments' + query).then(
            function (response) {
                self.rfiDocuments = response.data;
                self.isWaiting = false;
            },
            function () {
                self.isWaiting = false;
                alert('Problem baf/RfiDocuments');
            }
        )
    }

    self.btnRfiClick = function () {
        alert("RFI");
    }

});

