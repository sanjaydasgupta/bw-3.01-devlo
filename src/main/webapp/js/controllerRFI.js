app.controller("RFICtrl", function ($http, $log) {
    var self = this;

    self.personId = document.getElementById("hndLoggedInID").value;
    self.ownedActions = [];
    self.rfiDocuments = [];
    self.selectedAction = null;
    self.isWaiting = false;

    self.setupRfiDisplay = function (action) {
        self.selectedAction = null;
        $http.get('baf/OwnedActionsAll?person_id=' + self.personId).then(
            function (response) {
                self.ownedActions = response.data;
                self.selectedAction = action;
            }
        )
    }

    self.setupRfiDisplay(null);

    self.statusColor = function (action) {
        switch (action.display_status) {
        case "defined":
            return "yellow";
            break;
        case "waiting":
            return "magenta";
            break;
        case "waiting2":
            return "brown";
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

    self.rfiButtonClick = function () {
        var isRequest = self.selectedAction.assignee_is_user;
        self.isWaiting = true;
        $http({
            method: 'POST',
            url: 'baf/RfiSubmit',
            data: $.param({'project_id': self.selectedAction.project_id, 'action_name': self.selectedAction.name,
                'activity_id': self.selectedAction.activity_id, 'rfi_text': self.newRfiText, is_request: isRequest}),
            headers: {'Content-Type': 'application/x-www-form-urlencoded'}
        }).then(
            function() {
                self.isWaiting = false;
                self.newRfiText = '';
                self.setupRfiDisplay(self.selectedAction);
            },
            function (responseError) {
                self.isWaiting = false;
                alert("RFI-Send Problem");
            }
        );
    }

    self.rfiButtonText = function () {
        if (self.selectedAction.assignee_is_user) {
            return "Request";
        } else if (self.selectedAction.phase_manager_is_user) {
            return "Respond";
        }
    }

    self.selectedActionMayInput = function () {
        if (self.selectedAction.assignee_is_user) {
            return true;
        } else if (self.selectedAction.phase_manager_is_user) {
            var hasRequests = false;
            self.selectedAction.inDocuments.forEach(function (d) {
                if (d._id == "56fe4e6bd5d8ad3da60d5d38") {
                    hasRequests = true;
                }
            })
            return hasRequests;
        }
    }

});

