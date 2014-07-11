package de.identpro.plugin.nid;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.os.Handler;
import android.util.Log;
import de.identpro.lib.nid.rfid.ReaderEventHandler;
import de.identpro.lib.nid.rfid.ReaderProxy;
import de.identpro.lib.nid.rfid.ReaderSettings;
import de.identpro.lib.nid.rfid.RfidWriteResult;

/**
 * JSON protocol for communication: type: is 'barcode', 'rfid' or 'ops' msg: is context related
 * message like 'error_write' data: is an array with context related data
 * 
 * { "type": "rfid" "msg":"error_message", "data":[ "ABCDEFF1234567890", "123456712345F5678" ] }
 */
public class Rfid extends CordovaPlugin implements ReaderEventHandler {

  private final String TAG = "Rfid";

  private final static String RFID_STATUS_ON = "on";
  private final static String RFID_STATUS_OFF = "off";

  private final static String RES_TYPE_RFID = "rfid";
  private final static String RES_TYPE_OPS = "ops";

  private String status = RFID_STATUS_OFF;

  private CallbackContext callbackContext;

  public static final String ACTION_READER_START = "start";
  public static final String ACTION_READER_STOP = "stop";
  public static final String ACTION_INVENTORY_START = "read";
  public static final String ACTION_READER_STATUS = "status";
  public static final String ACTION_READER_WRITE = "write";
  public static final String ACTION_READER_SETTINGS = "settings";

  private List<String> tagList = new ArrayList<String>();

  private ReaderProxy readerProxy;


  private CallbackContext inventoryCallbackContext = null;

  private final Handler resultHandler = new Handler();

  private final Runnable mStatusChecker = new Runnable() {
    @Override
    public void run() {
      sendUpdate(createJson(null, "inventory", tagList), true);
    }
  };

  @Override
  public void initialize(CordovaInterface cordova, CordovaWebView webView) {
    super.initialize(cordova, webView);
    if (readerProxy == null) {
      readerProxy = new ReaderProxy(cordova.getActivity(), webView.getContext(), this);
    }
  }

  @Override
  public boolean execute(String action, JSONArray args, CallbackContext callbackContext)
      throws JSONException {
    try {

      String msg = "";
      if (ACTION_READER_START.equals(action)) {
        msg = "rader_" + ACTION_READER_START;
        Log.d(TAG, msg);
        success(createJson(msg), callbackContext);
        startReader();
        return true;
      }

      if (ACTION_READER_STOP.equals(action)) {

        this.inventoryCallbackContext = null;

        msg = "reader_" + ACTION_READER_STOP;
        Log.d(TAG, msg);
        success(createJson(msg), callbackContext);
        stopReader();
        return true;
      }

      if (ACTION_INVENTORY_START.equals(action)) {

        this.inventoryCallbackContext = callbackContext;

        msg = "reader_" + ACTION_INVENTORY_START;
        Log.d(TAG, msg);
        startInventory();
        success(createJson(msg), callbackContext);
        return true;
      }

      if (ACTION_READER_STATUS.equals(action)) {
        msg = "reader_" + ACTION_READER_STATUS;
        Log.d(TAG, msg);
        success(createJson(RES_TYPE_OPS, msg), callbackContext);
        return true;
      }

      if (ACTION_READER_WRITE.equals(action)) {
        JSONObject data = args.getJSONObject(0);
        String newEPC = data.getString("newEPC");
        String targetEPC = data.getString("targetEPC");
        msg =
            ACTION_READER_WRITE + "replace EPC: " + targetEPC + "with EPC:" + newEPC
                + " trigger was successful!!!";
        Log.d(TAG, msg);
        writeEPC(targetEPC, newEPC, callbackContext);
        return true;
      }

      if (ACTION_READER_SETTINGS.equals(action)) {
        JSONObject data = args.getJSONObject(0);
        JSONObject power = data.getJSONObject("power");

        if (power != null && power.has("level") == true) {
          msg = ACTION_READER_SETTINGS + " SET trigger was successful!!!";
          setPowerLevel(power.getInt("level"));
        }
        sendSettings(callbackContext);

        Log.d(TAG, msg);
        return true;
      }

      String err = "Invalid action";
      error(createJson(err), callbackContext);
      Log.e(TAG, err);
      return false;
    } catch (Exception e) {
      Log.e(TAG, "Exception: " + e.getMessage());
      error(createJson("Exception: " + e.getMessage()), callbackContext);
      return false;
    }
  }

  private void sendUpdate(JSONObject info, boolean keepCallback) {
    if (this.inventoryCallbackContext != null) {
      PluginResult result = new PluginResult(PluginResult.Status.OK, info);
      result.setKeepCallback(keepCallback);
      this.inventoryCallbackContext.sendPluginResult(result);
    }
  }

  private void startInventory() {
    cordova.getThreadPool().execute(new Runnable() {
      @Override
      public void run() {

        /*
         * Continuous inventory reading, where readings are received in onReaderReadTag()
         */
        readerProxy.startInventory();

      }
    });
  }

  private void stopInventory() {
    readerProxy.stopInventory();
  }

  private void stopReader() {
    cordova.getThreadPool().execute(new Runnable() {
      @Override
      public void run() {
        stopInventory();
        readerProxy.destroy();
        setStatus(RFID_STATUS_OFF);
      }
    });

  }

  private void startReader() {
    cordova.getThreadPool().execute(new Runnable() {
      @Override
      public void run() {
        readerProxy.init();
        startInventory();
        setStatus(RFID_STATUS_ON);
      }
    });
  }

  private void writeEPC(final String targetEPC, final String newEPC,
      final CallbackContext callbackContext) {
    cordova.getThreadPool().execute(new Runnable() {
      private RfidWriteResult writeResult;

      @Override
      public void run() {

        writeResult = readerProxy.writeWithVerify(targetEPC, newEPC);

        if (RfidWriteResult.SUCCESS != writeResult.getResult()) {
          error(createJson(writeResult.getMessage()), callbackContext);
        } else {
          error(createJson(writeResult.getMessage()), callbackContext);
        }
      }
    });

  }

  private void sendSettings(final CallbackContext callbackContext) {
    Map<String, String> power = new HashMap<String, String>();
    power.put("level", Integer.toString(getPowerLevel()));
    success(createJsonSettings("", "current_settings", power), callbackContext);
  }

  private void setPowerValue(ReaderProxy.Power power) {
    ReaderSettings powerSetting = readerProxy.getSettings();
    powerSetting.setPower(power.getValue());
    readerProxy.setSettings(powerSetting);
  }

  private ReaderProxy.Power getPowerValue() {
    ReaderSettings powerSetting = readerProxy.getSettings();
    return ReaderProxy.Power.getValueFromNumeric(powerSetting.getPower());
  }

  private int getPowerLevel() {
    ReaderSettings powerSetting = readerProxy.getSettings();
    ReaderProxy.Power power = ReaderProxy.Power.getValueFromNumeric(powerSetting.getPower());
    return ReaderProxy.Power.getLevel(power);
  }

  private void setPowerLevel(int level) {
    ReaderProxy.Power powerValue = ReaderProxy.Power.getValueByLevel(level);
    setPowerValue(powerValue);
  }

  private void error(JSONObject json, CallbackContext callbackContext) {
    PluginResult progressResult = new PluginResult(PluginResult.Status.ERROR, json);
    progressResult.setKeepCallback(true);
    callbackContext.sendPluginResult(progressResult);
  }

  private void success(JSONObject json, CallbackContext callbackContext) {
    PluginResult progressResult = new PluginResult(PluginResult.Status.OK, json);
    progressResult.setKeepCallback(true);
    callbackContext.sendPluginResult(progressResult);
  }

  private JSONObject createJson(String msg) {
    return createJson("", msg, new ArrayList<String>());
  }

  private JSONObject createJson(String type, String msg) {
    return createJson(type, msg, new ArrayList<String>());
  }

  private JSONObject createJson(String type, String msg, Collection<String> data) {
    JSONObject json = null;

    /* initialize if not already done */
    if (type == null || type.equals("")) {
      type = RES_TYPE_RFID;
    }

    if (msg == null) {
      msg = "";
    }

    JSONArray array = new JSONArray(data);
    try {
      json = new JSONObject();
      json.put("type", type);
      json.put("msg", msg);
      json.put("data", array);
    } catch (JSONException e) {
      Log.d(TAG, "Can not create JSON: " + e);
    }
    return json;
  }

  private JSONObject createJsonSettings(String type, String msg, Map<String, String> data) {
    JSONObject json = null;

    /* initialize if not already done */
    if (type == null || type.equals("")) {
      type = RES_TYPE_RFID;
    }

    if (msg == null) {
      msg = "";
    }

    JSONObject obj = new JSONObject(data);
    try {
      json = new JSONObject();
      json.put("type", type);
      json.put("msg", msg);
      json.put("data", obj);
    } catch (JSONException e) {
      Log.d(TAG, "Can not create JSON: " + e);
    }
    return json;
  }

  /*
   * propagate the life cycle to reader
   */

  @Override
  public void onDestroy() {
    super.onDestroy();
    if (readerProxy != null) {
      Log.d(TAG, "Reader destroy");
      readerProxy.onDestroy();
    }

  }

  @Override
  public void onPause(boolean multitasking) {
    super.onPause(multitasking);
    if (readerProxy != null) {
      Log.d(TAG, "Reader pause");
      readerProxy.onPause();
    }
  }

  @Override
  public void onReset() {
    super.onReset();
    if (readerProxy != null) {
      Log.d(TAG, "Reader reset");
      readerProxy.onStop();
    }
  }

  @Override
  public void onResume(boolean multitasking) {
    super.onResume(multitasking);
    if (readerProxy != null) {
      Log.d(TAG, "Reader resume");
      readerProxy.onResume();
    }
  }

  /*
   * implement the ReaderEventHandler interface
   */

  @Override
  public void onReaderIncomingMsg() {
    // TODO Auto-generated method stub
  }

  @Override
  public void onReaderStateChange(int state) {
    String status = "";
    switch (state) {
      case ReaderProxy.READER_CONNECTED:
        status = "'connected'";
        break;
      case ReaderProxy.READER_DISCONNECTED:
        status = "'disconnected'";
        break;
    }
  }

  @Override
  public void onReaderReadTag(int event, Collection<String> tags) {
    resultHandler.removeCallbacks(mStatusChecker);

    Log.d(TAG, "### Receive new TAGs: " + tags.size());
    for (String tag : tags) {
      Log.d(TAG, tag);
    }

    tagList = new ArrayList<String>(tags);
    resultHandler.postDelayed(mStatusChecker, 1);

  }

  @Override
  public void onReaderResponse(int event, String code) {
    // TODO Auto-generated method stub

  }

  @Override
  public void onReaderProperty(char code, String value) {
    // TODO Auto-generated method stub

  }

  @Override
  public void onReaderAction(char code) {
    // TODO Auto-generated method stub

  }

  @Override
  public void onReaderExtentedProperty(char code, String value) {
    // TODO Auto-generated method stub

  }

  @Override
  public void onReaderTimeout() {
    // TODO Auto-generated method stub

  }

  @Override
  public void onReaderToast(String msg) {
    // TODO Auto-generated method stub

  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }
}
