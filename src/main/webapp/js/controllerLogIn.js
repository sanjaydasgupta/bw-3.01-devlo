app.controller("BuildWhizNgAppLogInCtrl", function ($http, $scope, BuildWhizNgAppLogInService) {
    document.getElementById("inputEmail").focus();
    $scope.email = "tester@buildwhiz.com";
    //=====check validation====
    function CheckLogInForm() {
        var bValidate = true;
        var sMsg = "";
        document.getElementById('lblMsg').innerHTML = "";
        $(".msgToggle").hide();

        if (inputEmail.value == "email" || inputEmail.value == "") {
            sMsg += "Email cannot be blank\r\n </br>";
            bValidate = false;
        }

        var pattern = /^\s*[\w\-\+_]+(\.[\w\-\+_]+)*\@[\w\-\+_]+\.[\w\-\+_]+(\.[\w\-\+_]+)*\s*$/;

        //if (!inputEmail.value.match(pattern)) {
        //    sMsg += "Invalid Email\r\n </br>";
        //    bValidate = false;
        //}
        //if (!pattern.test(str) && bValidate) {
        //    sMsg += "Invalid Email\r\n </br>";
        //    bValidate = false;
        //}

        if (inputPassword.value == "password" || inputPassword.value == "") {
            sMsg += "Password cannot be blank\r\n </br>";
            bValidate = false;
        }

        if (!bValidate) {
            $(".msgToggle").show();
            document.getElementById('lblMsg').innerHTML = sMsg;
        }
      
        return bValidate;
    }

    //LogIn Button Click 
    $scope.clickLogIn = function () {
        if (CheckLogInForm()) {
            
            var sID = "";
            var sFirstName = "";
            var sLastName = "";
            var sProjectManager = "n";

            var Data = BuildWhizNgAppLogInService.UserAuthenticate($scope.email, $scope.password);
            Data.then(function (response) {
                $scope.UserAuthentication = response.data;
                //alert(JSON.stringify(response.data));  

                var key = "";
                var value = "";
                var valueOmniClass = "";
                for (var keyName in $scope.UserAuthentication) {
                    key = keyName;
                    value = $scope.UserAuthentication[keyName];
                    value = value.toString().trim();
                    //alert(key + value);
                    //alert(value);
                    if (key == "_id") {
                        sID = value;                       
                    }

                    else if (key == "first_name") {
                        sFirstName = value;
                      
                    }

                    else if (key == "last_name") {
                        sLastName = value;
                    }

                    else if (key == "omniclass34roles") {                       

                        var arrOmniClass = new Array();
                        arrOmniClass = value.split(",");
                        for (var idx = 0; idx < arrOmniClass.length; idx++) {                           
                            if (arrOmniClass[idx].toString() == "34-55 14 19 XX") {                                
                                sProjectManager = "y";
                            }                           
                        }
                    }
                  
                }
              
                if (sID != "") {                                    
                     //alert(sID);
                    $("#divWelcomeMsg").show();
                    location.hash = "#!/dashboard";

                    document.getElementById("hndLoggedInID").value = sID;
                    document.getElementById("hndLoggedInFName").value = sFirstName;
                    document.getElementById("hndLoggedInLName").value = sLastName;

                    document.getElementById("hndPersonID").value = sID;
                    document.getElementById("hndPersonFName").value = sFirstName;
                    document.getElementById("hndPersonLName").value = sLastName;

                    document.getElementById("hdnLoggedInProjectManager").value = sProjectManager;

                    document.getElementById("lblWelcomePerson").innerHTML = "Welcome " + document.getElementById("hndLoggedInFName").value + " " + document.getElementById("hndLoggedInLName").value;
                    document.getElementById('ttlBuildWhizMain').innerHTML = "BW " + document.getElementById("hndLoggedInFName").value + " " + document.getElementById("hndLoggedInLName").value;
                  


                }
                else {
                 
                    document.getElementById("hndLoggedInID").value = "";
                    document.getElementById("hndLoggedInFName").value = "";
                    document.getElementById("hndLoggedInLName").value = "";

                    document.getElementById("hndPersonID").value = "";
                    document.getElementById("hndPersonFName").value = "";
                    document.getElementById("hndPersonLName").value = "";

                    document.getElementById("hdnLoggedInProjectManager").value = "n";

                    document.getElementById("lblWelcomePerson").innerHTML = "";

                    $("#divWelcomeMsg").hide();
                    $(".msgToggle").show();
                    document.getElementById('lblMsg').innerHTML = "Invalid Email or Password";
                    document.getElementById('ttlBuildWhizMain').innerHTML = "BuildWhiz";

                }

                if (document.getElementById('hdnLoggedInProjectManager').value == 'y') {
                    gProjectManager = true;
                }

            }, function (responseError) {
                $(".msgToggle").show();
                document.getElementById('lblMsg').innerHTML = serviceErrorMessege;

            });

        } // checkForm() end
       
    } // clickLogIn() end
  
});
