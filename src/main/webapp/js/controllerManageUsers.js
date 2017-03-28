angular.module('BuildWhizApp')

.controller("ManageUsersCtrl", ['$log', '$http', 'AuthenticationService',
      function ($log, $http, AuthService) {

  var self = this;
  self.nameFilter = '';
  self.users = [];
  self.selectedUser = null;
  self.allRoles = [];
  self.userRoles = [];

  self.newUserFirstName = '';
  self.newUserLastName = '';
  self.newUserEmailWork = '';
  self.newUserEmailOther = '';
  self.newUserPhoneWork = '';
  self.newUserPhoneMobile = '';

  self.userEmailWork = '';
  self.userEmailOther = '';
  self.userPhoneWork = '';
  self.userPhoneMobile = '';
  self.userEmailWorkOriginal = '';
  self.userEmailOtherOriginal = '';
  self.userPhoneWorkOriginal = '';
  self.userPhoneMobileOriginal = '';

  $http.get('api/Role').then(
    function(res) {
      self.allRoles = res.data;
      $log.log('Got ' + self.allRoles.length + ' roles');
    },
    function(res) {
      $log.log('ERROR failed to get roles');
    }
  )

  self.findUsers = function() {
    var query = 'api/Person/{$or: [{first_name: {$regex: "' + self.nameFilter + '", $options: "i"}},' +
        '{last_name: {$regex: "' + self.nameFilter + '", $options: "i"}}]}';
    $log.log('GET ' + query);
    $http.get(query).then(
      function(res) {
        self.users = res.data;
        self.selectedUser = null;
        self.deselectUser();
      },
      function(res) {
        $log.log('ERROR GET ' + query);
      }
    )
  }

  self.enableDisableLogin = function() {
    var query = 'baf/UserPropertySet?person_id=' + self.selectedUser._id + '&property=enabled&value=' +
        self.selectedUser.enabled;
    $log.log('POST ' + query);
    $http.post(query).then(
      function(res) {
        $log.log('OK POST ' + query);
      },
      function(res) {
        $log.log('ERROR POST ' + query);
      }
    )
  }

  self.enableDisableEmail = function() {
    var query = 'baf/UserPropertySet?person_id=' + self.selectedUser._id + '&property=email_enabled&value=' +
        self.selectedUser.email_enabled;
    $log.log('POST ' + query);
    $http.post(query).then(
      function(res) {
        $log.log('OK POST ' + query);
      },
      function(res) {
        $log.log('ERROR POST ' + query);
      }
    )
  }

  self.selectUser = function(user) {
    if (!user.hasOwnProperty('enabled')) {
      user.enabled = false;
    }
    if (!user.hasOwnProperty('email_enabled')) {
      user.email_enabled = false;
    }
    self.userRoles = [];
    self.allRoles.forEach(function(role) {
      var roleKey = role.category + ':' + role.name;
      var roleDisplay = role.category + ' / ' + role.name;
      var newRole = {key: roleKey, ok: false, display: roleDisplay}
      user.roles.forEach(function(ur) {
        if (ur == roleKey) {
          newRole.ok = true;
        }
      })
      self.userRoles.push(newRole);
    })
    user.emails.forEach(function(email) {
      if (email.type == 'work') {
        self.userEmailWork = self.userEmailWorkOriginal = email.email;
      } else if (email.type == 'other') {
        self.userEmailOther = self.userEmailOtherOriginal = email.email;
      }
    });
    user.phones.forEach(function(phone) {
      if (phone.type == 'work') {
        self.userPhoneWork = self.userPhoneWorkOriginal = phone.phone;
      } else if (phone.type == 'mobile') {
        self.userPhoneMobile = self.userPhoneMobileOriginal = phone.phone;
      }
    });
    self.selectedUser = user;
  }

  self.roleToggle = function(role) {
    var query = 'baf/UserPropertySet?person_id=' + self.selectedUser._id + '&property=role:' + role.key +
        '&value=' + role.ok;
    $log.log('POST ' + query);
    $http.post(query).then(
      function(res) {
        $log.log('OK POST ' + query);
        if (role.ok) {
          self.selectedUser.roles.push(role.key);
        } else {
          self.selectedUser.roles = self.selectedUser.roles.filter(function(r) {return r != role.key})
        }
      },
      function(res) {
        $log.log('ERROR POST ' + query);
      }
    )
  }

  self.addNewUserDisabled = function() {
    var re = /^[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,3}$/
    return self.newUserFirstName.trim() == '' || self.newUserLastName.trim() == '' ||
        !re.test(self.newUserEmailWork.trim());
  }

  self.addNewUser = function() {
    $log.log('Called addNewUser(): ' + JSON.stringify(newUser));
    var newUser = {first_name: self.newUserFirstName, last_name: self.newUserLastName,
        emails: [{type: 'work', email: self.newUserEmailWork}], phones: [], tz: 'US/Pacific'};
    if (self.newUserEmailOther.trim() != '') {
      newUser.emails.push({type: 'other', email: self.newUserEmailOther});
    }
    if (self.newUserPhoneWork.trim() != '') {
      newUser.phones.push({type: 'work', phone: self.newUserPhoneWork});
    }
    if (self.newUserPhoneMobile.trim() != '') {
      newUser.phones.push({type: 'mobile', phone: self.newUserPhoneMobile});
    }
    $http.post('api/Person', newUser).then(
      function() {
        $log.log('User ' + self.newUserFirstName + ' added');
        self.newUserFirstName = '';
        self.newUserLastName = '';
        self.newUserEmailWork = '';
        self.newUserEmailOther = '';
        self.newUserPhoneWork = '';
        self.newUserPhoneMobile = '';
      },
      function(res) {
        alert('ERROR adding ' + self.newUserFirstName + '. Check input fields.');
      }
    )
  }

  self.deselectUser = function() {
    self.userEmailWork = '';
    self.userEmailOther = '';
    self.userPhoneWork = '';
    self.userPhoneMobile = '';
    $log.log('Called deselectUser()')
  }

  self.userContactsSet = function() {
    $log.log('Called userContactSet()')
    var parameters = [];
    var values = [];
    if (self.userEmailWork != self.userEmailWorkOriginal) {
      parameters.push("email_work");
      values.push(self.userEmailWork);
    }
    if (self.userEmailOther != self.userEmailOtherOriginal) {
      parameters.push("email_other");
      values.push(self.userEmailOther);
    }
    if (self.userPhoneWork != self.userPhoneWorkOriginal) {
      parameters.push("phone_work");
      values.push(self.userPhoneWork);
    }
    if (self.userPhoneMobile != self.userPhoneMobileOriginal) {
      parameters.push("phone_mobile");
      values.push(self.userPhoneMobile);
    }
    var parameterString = parameters.join('|');
    var valueString = values.join('|');
    var query = 'baf/UserPropertySet?person_id=' + self.selectedUser._id + '&property=' + parameterString +
        '&value=' + valueString;
    $log.log('POST ' + query);
    $http.post(query).then(
      function(res) {
        $log.log('OK POST ' + query);
      },
      function(res) {
        alert('ERROR POST ' + query);
      }
    )
  }

  self.userContactsButtonsDisabled = function() {
    return self.selectedUser == null || (self.userEmailWork == self.userEmailWorkOriginal &&
        self.userEmailOther == self.userEmailOtherOriginal && self.userPhoneWork == self.userPhoneWorkOriginal &&
        self.userPhoneMobile == self.userPhoneMobileOriginal);
  }

  self.userContactsReset = function() {
    self.userEmailWork = self.userEmailWorkOriginal;
    self.userEmailOther = self.userEmailOtherOriginal;
    self.userPhoneWork = self.userPhoneWorkOriginal;
    self.userPhoneMobile = self.userPhoneMobileOriginal;
    $log.log('Called userContactReset()')
  }

  self.isSelf = function() {
    return self.selectedUser._id == AuthService.data._id;
  }

}]);