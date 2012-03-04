/**
 * 
 */
package uk.ac.horizon.ubihelper.service.channel;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.telephony.CellLocation;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;
import uk.ac.horizon.ubihelper.channel.NamedChannel;

/**
 * @author cmg
 *
 */
public class CellStrengthChannel extends NamedChannel  {
	private TelephonyManager telephony;
	private Service service;
	/**
	 * @param name
	 */
	public CellStrengthChannel(Service service, String name) {
		super(name);
		this.service = service;
		telephony = (TelephonyManager)service.getSystemService(Service.TELEPHONY_SERVICE);
	}
	
	private PhoneStateListener listener = new PhoneStateListener() {

		@Override
		public void onCellLocationChanged(CellLocation location) {
			update(location, null);
		}

		/* (non-Javadoc)
		 * @see android.telephony.PhoneStateListener#onSignalStrengthsChanged(android.telephony.SignalStrength)
		 */
		@Override
		public void onSignalStrengthsChanged(SignalStrength signalStrength) {
			update(null, signalStrength);
		}
		
	};
	@Override
	protected void handleStart() {
		if (telephony!=null) {
			telephony.listen(listener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS | PhoneStateListener.LISTEN_CELL_LOCATION);
		}
	}
	protected void update(CellLocation loc, SignalStrength ss) {
		if (loc==null && telephony!=null)
			loc = telephony.getCellLocation();
		// TODO Auto-generated method stub
		JSONObject value = new JSONObject();
		try {
			value.put("timestamp", System.currentTimeMillis());
			if (loc instanceof GsmCellLocation) {
				GsmCellLocation gsm = (GsmCellLocation)loc;
				if (gsm.getCid()!=(-1))
					value.put("cid", gsm.getCid());
				if (gsm.getLac()!=(-1))
					value.put("lac", gsm.getLac());
				value.put("type","gsm");
			}
			else if (loc instanceof CdmaCellLocation) {
				CdmaCellLocation cdma = (CdmaCellLocation)loc;
				if (cdma.getBaseStationId()!=(-1))
					value.put("baseStationId", cdma.getBaseStationId());
				if (cdma.getBaseStationLatitude()!=Integer.MAX_VALUE)
					value.put("baseStationLat", cdma.getBaseStationLatitude());
				if (cdma.getBaseStationLongitude()!=Integer.MAX_VALUE)
					value.put("baseStationLon", cdma.getBaseStationLongitude());
				if (cdma.getNetworkId()!=(-1))
					value.put("baseStationId", cdma.getNetworkId());
				if (cdma.getNetworkId()!=(-1))
					value.put("networkId", cdma.getNetworkId());
				if (cdma.getSystemId()!=(-1))
					value.put("systemId", cdma.getSystemId());
				value.put("type","cdma");
			}
			else if (loc!=null) {
				value.put("type", loc.getClass().getName());
			}

			if (ss!=null) {
				if (ss.getCdmaDbm()!=(-1))
					value.put("cdmsDbm", ss.getCdmaDbm());
				if (ss.getCdmaEcio()!=(-1))
					value.put("cdmaEcio", ss.getCdmaEcio());
				if (ss.getEvdoDbm()!=(-1))
					value.put("evdoDbm", ss.getEvdoDbm());
				if (ss.getEvdoEcio()!=(-1))
					value.put("evdiEcio", ss.getEvdoEcio());
				if (ss.getEvdoSnr()!=(-1))
					value.put("evdoSnr", ss.getEvdoSnr());
				if (ss.getGsmBitErrorRate()!=(-1))
					value.put("gsmBER", ss.getGsmBitErrorRate());
				if (ss.getGsmSignalStrength()!=(-1))
					value.put("gsmSS", ss.getGsmSignalStrength());
				value.put("gsm", ss.isGsm());
			}
		} catch (JSONException e) {
		 	// shouldn't
		}
		onNewValue(value);
	}
	@Override
	protected void handleStop() {
		if (telephony!=null)
			telephony.listen(listener, PhoneStateListener.LISTEN_NONE);
	}

}
