app.controller("UserRolesCtrl", function ($log, $http) {

    var self = this;
    self.persons = [];

    self.initPage = function() {
        self.isWaiting = true;
        $log.log('HTTP GET baf/Person')
        $http.get('api/Person').then(
            function (response) {
                self.persons = response.data;
                self.isWaiting = false;
                for (var i = 0; i < self.persons.length; ++i) {
                    var person = self.persons[i];
                    if (person.first_name == "No" && person.last_name == "One") {
                        self.persons.splice(i, 1);
                        break;
                    }
                }
                self.persons.forEach(function(person) {self.initPerson(person);});
            },
            function() {
                alert('ERROR: HTTP GET api/Person');
            }
        );
    }

    self.initPerson = function(person) {
        person.roleDemo = person.newRoleDemo = person.roles.join(',').indexOf('BW-Demo') != -1;
        person.roleAdmin = person.newRoleAdmin = person.roles.join(',').indexOf('BW-Admin') != -1;
    }

    self.savePerson = function(person) {
        self.isWaiting = true;
        var query = '?person_id=' + person._id + '&BW-Demo=' + person.newRoleDemo +
                '&BW-Admin=' + person.newRoleAdmin;
        $log.log('HTTP POST baf/UserSkillsSet/' + query)
        $http.post('baf/UserSkillsSet/' + query).then(
            function(response) {
                //alert("Person record updated");
                self.isWaiting = false;
                self.initPage();
            },
            function(response) {
                alert('ERROR: api/Person/');
                self.isWaiting = false;
            }
        )
    }

    self.dataModified = function(person) {
        return (person.roleDemo != person.newRoleDemo) || (person.roleAdmin != person.newRoleAdmin);
    }

    self.initPage();

});