var cordova = require('cordova'),
    exec = require('cordova/exec');

var RfidNid = function() {
        this.options = {};
};

RfidNid.prototype = {
  createEvent: function(actionName, params, successCallback, errorCallback) {
      cordova.exec(
          successCallback, // success callback function
          errorCallback, // error callback function
          'RfidNid', // mapped to our native Java class, by entry in config.xml <feature name="RfidNid">
          actionName, // with this action name
          [params]
      );
   },
   
   stop: function (successCallback, errorCallback) {
      this.createEvent('stop', null, successCallback, errorCallback);
   },

   start: function (successCallback, errorCallback) {
      this.createEvent('start', null, successCallback, errorCallback);
   },

   read: function (successCallback, errorCallback) {
      this.createEvent('read', null, successCallback, errorCallback);
   },
   
   status: function (successCallback, errorCallback) {
     this.createEvent('status', null, successCallback, errorCallback);
   },
   
   /**
    * data: {"newEPC" : "0123456789ABCDEF12345678", "targetEPC" : "9876543210ABCDEF12345678"}
    */
   write: function (successCallback, errorCallback, data) {
     this.createEvent('write', data, successCallback, errorCallback);
   },
   
   /**
    * Set power level:
    * 
    * data: {
    * 	power: {
    *     level: 1
    * 	}
    * }
    * 
    * return always all settings 
    */
   settings: function(successCallback, errorCallback, data) {
     this.createEvent('settings', data, successCallback, errorCallback);
   }
};

var RfidNidInstance = new RfidNid();

module.exports = RfidNidInstance;

