package com.zebrarfidbarcode.rfid;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.zebra.rfid.api3.ACCESS_OPERATION_STATUS;
import com.zebra.rfid.api3.ENUM_TRANSPORT;
import com.zebra.rfid.api3.HANDHELD_TRIGGER_EVENT_TYPE;
import com.zebra.rfid.api3.INVENTORY_STATE;
import com.zebra.rfid.api3.Antennas;
import com.zebra.rfid.api3.InvalidUsageException;
import com.zebra.rfid.api3.OperationFailureException;
import com.zebra.rfid.api3.RFIDReader;
import com.zebra.rfid.api3.ReaderDevice;
import com.zebra.rfid.api3.Readers;
import com.zebra.rfid.api3.RfidEventsListener;
import com.zebra.rfid.api3.RfidReadEvents;
import com.zebra.rfid.api3.RfidStatusEvents;
import com.zebra.rfid.api3.SESSION;
import com.zebra.rfid.api3.SL_FLAG;
import com.zebra.rfid.api3.START_TRIGGER_TYPE;
import com.zebra.rfid.api3.STATUS_EVENT_TYPE;
import com.zebra.rfid.api3.STOP_TRIGGER_TYPE;
import com.zebra.rfid.api3.TagData;
import com.zebra.rfid.api3.TagDataArray;
import com.zebra.rfid.api3.TriggerInfo;

import java.util.ArrayList;

public class RFIDReaderInterface implements RfidEventsListener {
  private IRFIDReaderListener listener;

  private final String TAG = "RFIDReaderIml";
  private int MAX_POWER = 270;
  private Readers readers;
  private ArrayList<ReaderDevice> availableRFIDReaderList;
  public static ReaderDevice readerDevice;
  private RFIDReader reader;

  public RFIDReaderInterface(IRFIDReaderListener listener) {
    this.listener = listener;
  }

  public ArrayList<ReaderDevice> getAvailableReaders() {
    try {
      return readers.GetAvailableRFIDReaderList();
    } catch (Exception e) {
      return new ArrayList<>();
    }
  }

  public void connect(Context context, String scannerName) {
    // Init Readers
    readers = new Readers(context, ENUM_TRANSPORT.ALL);
    try {
      availableRFIDReaderList = readers.GetAvailableRFIDReaderList();
      if (availableRFIDReaderList != null && !availableRFIDReaderList.isEmpty()) {
        // get first reader from list
        for (ReaderDevice rfid : availableRFIDReaderList) {
          if (rfid.getName().equals(scannerName)) {
            readerDevice = rfid;
            reader = readerDevice.getRFIDReader();
            if (!reader.isConnected()) {
              reader.connect();
              configureReader();
            }
          }
        }
      }
    } catch (InvalidUsageException | OperationFailureException e) {
      e.printStackTrace();
    }
  }

  private void configureReader() {
    if (reader.isConnected()) {
      TriggerInfo triggerInfo = new TriggerInfo();
      triggerInfo.StartTrigger.setTriggerType(START_TRIGGER_TYPE.START_TRIGGER_TYPE_IMMEDIATE);
      triggerInfo.StopTrigger.setTriggerType(STOP_TRIGGER_TYPE.STOP_TRIGGER_TYPE_IMMEDIATE);
      try {
        // receive events from reader
        reader.Events.addEventsListener(this);
        // HH event
        reader.Events.setHandheldEvent(true);
        // tag event with tag data
        reader.Events.setTagReadEvent(true);
        reader.Events.setAttachTagDataWithReadEvent(false);
        // set start and stop triggers
        reader.Config.setStartTrigger(triggerInfo.StartTrigger);
        reader.Config.setStopTrigger(triggerInfo.StopTrigger);
        // power levels are index based so maximum power supported get the last one
        MAX_POWER = reader.ReaderCapabilities.getTransmitPowerLevelValues().length - 1;
        // set antenna configurations
        Antennas.AntennaRfConfig config = reader.Config.Antennas.getAntennaRfConfig(1);
        config.setTransmitPowerIndex(MAX_POWER);
        config.setrfModeTableIndex(0);
        config.setTari(0);
        reader.Config.Antennas.setAntennaRfConfig(1, config);
        // Set the singulation control
        Antennas.SingulationControl s1_singulationControl = reader.Config.Antennas.getSingulationControl(1);
        s1_singulationControl.setSession(SESSION.SESSION_S0);
        s1_singulationControl.Action.setInventoryState(INVENTORY_STATE.INVENTORY_STATE_A);
        s1_singulationControl.Action.setSLFlag(SL_FLAG.SL_ALL);
        reader.Config.Antennas.setSingulationControl(1, s1_singulationControl);
        // delete any prefilters
        reader.Actions.PreFilters.deleteAll();
      } catch (InvalidUsageException | OperationFailureException e) {
        e.printStackTrace();
      }
    }
  }

  @Override
  public void eventReadNotify(RfidReadEvents rfidReadEvents) {
    TagData[] readTags = reader.Actions.getReadTags(100);
    if (readTags != null) {
      ArrayList<String> listTags = new ArrayList<>();
      for (TagData myTag : readTags) {
        String tagID = myTag.getTagID();

        if (tagID != null) {
          listTags.add(tagID);
        }
      }
      listener.onRFIDRead(listTags);
    }
  }

  @SuppressLint("StaticFieldLeak")
  public void eventStatusNotify(RfidStatusEvents rfidStatusEvents) {
    Log.d(TAG, "Status Notification: " + rfidStatusEvents.StatusEventData.getStatusEventType());
    if (rfidStatusEvents.StatusEventData.getStatusEventType() == STATUS_EVENT_TYPE.HANDHELD_TRIGGER_EVENT) {
      if (rfidStatusEvents.StatusEventData.HandheldTriggerEventData.getHandheldEvent() == HANDHELD_TRIGGER_EVENT_TYPE.HANDHELD_TRIGGER_PRESSED) {
        listener.onTriggerPressed();
        new AsyncTask<Void, Void, Void>() {
          @Override
          protected Void doInBackground(Void... voids) {
            try {
              reader.Actions.Inventory.perform();
            } catch (InvalidUsageException | OperationFailureException e) {
              e.printStackTrace();
            }
            return null;
          }
        }.execute();
      }
      if (rfidStatusEvents.StatusEventData.HandheldTriggerEventData.getHandheldEvent() == HANDHELD_TRIGGER_EVENT_TYPE.HANDHELD_TRIGGER_RELEASED) {
        listener.onTriggerReleased();
        new AsyncTask<Void, Void, Void>() {
          @Override
          protected Void doInBackground(Void... voids) {
            try {
              reader.Actions.Inventory.stop();
            } catch (InvalidUsageException | OperationFailureException e) {
              e.printStackTrace();
            }
            return null;
          }
        }.execute();
      }
    }
  }

  public void onDestroy() {
    try {
      reader.Events.removeEventsListener(this);
      reader.disconnect();
      reader.Dispose();
      readers.Dispose();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
